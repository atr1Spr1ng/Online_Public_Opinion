package com.bupt.publicopinion.event.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.analysis.client.PythonIntelligenceClient;
import com.bupt.publicopinion.analysis.entity.ArticleSentiment;
import com.bupt.publicopinion.analysis.exception.IntelligenceServiceException;
import com.bupt.publicopinion.analysis.mapper.ArticleSentimentMapper;
import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.mapper.ArticleCleanMapper;
import com.bupt.publicopinion.event.entity.Event;
import com.bupt.publicopinion.event.entity.EventArticle;
import com.bupt.publicopinion.event.exception.EventNotFoundException;
import com.bupt.publicopinion.event.mapper.EventArticleMapper;
import com.bupt.publicopinion.event.mapper.EventMapper;
import com.bupt.publicopinion.event.service.EventService;
import com.bupt.publicopinion.event.vo.EventDetailVO;
import com.bupt.publicopinion.event.vo.EventVO;
import com.bupt.publicopinion.event.vo.SimilarEventResult;
import com.bupt.publicopinion.search.document.EventDocument;
import com.bupt.publicopinion.search.document.EventSimilarHit;
import com.bupt.publicopinion.search.service.SearchSyncService;
import com.bupt.publicopinion.system.entity.UserDomain;
import com.bupt.publicopinion.system.entity.UserKeyword;
import com.bupt.publicopinion.system.service.UserPreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EventServiceImpl implements EventService {

    private static final int MAX_ARTICLES_FOR_CLUSTERING = 5000;

    private final EventMapper eventMapper;
    private final EventArticleMapper eventArticleMapper;
    private final ArticleCleanMapper articleCleanMapper;
    private final ArticleSentimentMapper articleSentimentMapper;
    private final PythonIntelligenceClient pythonIntelligenceClient;
    private final SearchSyncService searchSyncService;
    private final UserPreferenceService userPreferenceService;

    public EventServiceImpl(
            EventMapper eventMapper,
            EventArticleMapper eventArticleMapper,
            ArticleCleanMapper articleCleanMapper,
            ArticleSentimentMapper articleSentimentMapper,
            PythonIntelligenceClient pythonIntelligenceClient,
            SearchSyncService searchSyncService,
            UserPreferenceService userPreferenceService
    ) {
        this.eventMapper = eventMapper;
        this.eventArticleMapper = eventArticleMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.articleSentimentMapper = articleSentimentMapper;
        this.pythonIntelligenceClient = pythonIntelligenceClient;
        this.searchSyncService = searchSyncService;
        this.userPreferenceService = userPreferenceService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> clusterAndSave(double threshold) {
        // 1. 取文章，限流
        List<ArticleClean> articles = articleCleanMapper.selectList(
                new LambdaQueryWrapper<ArticleClean>()
                        .isNotNull(ArticleClean::getKeywords)
                        .ne(ArticleClean::getKeywords, "")
                        .last("LIMIT " + MAX_ARTICLES_FOR_CLUSTERING)
        );

        List<Map<String, Object>> articleItems = new ArrayList<>();
        for (ArticleClean a : articles) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("title", a.getTitle() != null ? a.getTitle() : "");
            item.put("keywords", a.getKeywords() != null ? a.getKeywords() : "");
            item.put("summary", a.getSummary() != null ? a.getSummary() : "");
            item.put("published_at", a.getPublishedAt() != null ? a.getPublishedAt() : "");
            articleItems.add(item);
        }

        // 2. 调 Python 聚类
        PythonIntelligenceClient.ClusterResult result;
        try {
            result = pythonIntelligenceClient.clusterEvents(articleItems, threshold);
        } catch (IntelligenceServiceException e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("message", e.getMessage());
            errorResult.put("events", List.of());
            return errorResult;
        }

        // 3. 保存旧事件数据，用于 ID 继承匹配
        List<Event> oldEvents = eventMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, Set<Long>> oldEventArticles = new HashMap<>(); // eventId → cleanIds
        for (Event oe : oldEvents) {
            List<EventArticle> eas = eventArticleMapper.selectList(
                    new LambdaQueryWrapper<EventArticle>().eq(EventArticle::getEventId, oe.getId())
            );
            oldEventArticles.put(oe.getId(),
                    eas.stream().map(EventArticle::getCleanId).collect(Collectors.toSet()));
        }

        // 4. 收集所有有效 cleanId
        Set<Long> validCleanIds = articles.stream()
                .map(ArticleClean::getId)
                .collect(Collectors.toSet());

        // 5. 分类（LLM + 关键词词典）
        Map<Long, String> categoryMap = new HashMap<>();
        if (!result.events().isEmpty()) {
            try {
                List<Map<String, Object>> eventItems = new ArrayList<>();
                for (int i = 0; i < result.events().size(); i++) {
                    var item = result.events().get(i);
                    Map<String, Object> ei = new HashMap<>();
                    ei.put("event_id", i);
                    ei.put("title", item.title());
                    ei.put("keywords", item.keywords());
                    ei.put("article_count", item.articleCount());
                    ei.put("hotness", item.hotness());
                    eventItems.add(ei);
                }
                List<Map<String, Object>> classified = pythonIntelligenceClient.classifyTopics(eventItems);
                for (Map<String, Object> ce : classified) {
                    int idx = ((Number) ce.get("event_id")).intValue();
                    String cat = (String) ce.get("category");
                    if (idx < result.events().size()) {
                        categoryMap.put((long) idx, cat != null ? cat : "其他");
                    }
                }
            } catch (Exception e) {
                System.err.println("[Topic] 主题分类失败: " + e.getMessage());
            }
        }

        // 6. 新簇 → 旧事件 ID 匹配（≥50% 旧事件文章在新簇中 → 继承 ID）
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Map<Integer, Long> inheritedIds = new HashMap<>();  // newClusterIdx → oldEventId
        Set<Long> matchedOldEventIds = new HashSet<>();

        for (int i = 0; i < result.events().size(); i++) {
            Set<Long> newArticleIds = new HashSet<>(result.events().get(i).articleIds());
            Long bestOldId = null;
            double bestOverlap = 0.0;

            for (var entry : oldEventArticles.entrySet()) {
                Long oldId = entry.getKey();
                Set<Long> oldIds = entry.getValue();
                if (oldIds.isEmpty() || matchedOldEventIds.contains(oldId)) continue;

                long overlap = oldIds.stream().filter(newArticleIds::contains).count();
                double ratio = (double) overlap / oldIds.size();

                if (ratio >= 0.5 && ratio > bestOverlap) {
                    bestOverlap = ratio;
                    bestOldId = oldId;
                }
            }

            if (bestOldId != null) {
                inheritedIds.put(i, bestOldId);
                matchedOldEventIds.add(bestOldId);
            }
        }

        // 7. 清除所有旧 event_article 关联
        eventArticleMapper.delete(new LambdaQueryWrapper<>());

        // 8. 删除未匹配的旧事件（噪声/不合理事件自动清理）
        for (Event oe : oldEvents) {
            if (!matchedOldEventIds.contains(oe.getId())) {
                eventMapper.deleteById(oe.getId());
            }
        }

        // 9. 清除并重建 ES 事件索引
        try {
            searchSyncService.deleteAllEvents();
        } catch (Exception ex) {
            System.err.println("[Event] ES 事件索引清除失败（ES 可能未启动）: " + ex.getMessage());
        }

        // 10. 写入事件：已匹配的更新，未匹配的新建
        List<EventVO> savedEvents = new ArrayList<>();
        Long userId = UserContext.getRequired().userId();

        for (int i = 0; i < result.events().size(); i++) {
            PythonIntelligenceClient.EventClusterItem item = result.events().get(i);
            Event event = new Event();
            event.setTitle(item.title());
            event.setKeywords(String.join(",", item.keywords()));
            event.setArticleCount(item.articleCount());
            event.setHotness(BigDecimal.valueOf(item.hotness()));
            event.setLifecycle(item.lifecycle());
            event.setCategory(categoryMap.getOrDefault((long) i, "其他"));

            if (!item.startTime().isEmpty()) {
                event.setStartTime(parseDateTime(item.startTime()));
            }
            if (!item.endTime().isEmpty()) {
                event.setEndTime(parseDateTime(item.endTime()));
            }

            event.setUserId(userId);

            Long inheritedId = inheritedIds.get(i);
            if (inheritedId != null) {
                // 更新已有事件，保留 ID
                event.setId(inheritedId);
                eventMapper.updateById(event);
            } else {
                // 新建事件
                eventMapper.insert(event);
            }

            // 写入事件-文章关联
            for (Long cleanId : item.articleIds()) {
                if (!validCleanIds.contains(cleanId)) continue;
                EventArticle ea = new EventArticle();
                ea.setEventId(event.getId());
                ea.setCleanId(cleanId);
                eventArticleMapper.insert(ea);
            }

            savedEvents.add(EventVO.from(event));
        }

        // 11. 同步事件到 ES
        List<Event> allCurrentEvents = eventMapper.selectList(new LambdaQueryWrapper<>());
        try {
            searchSyncService.indexEvents(allCurrentEvents);
        } catch (Exception ex) {
            System.err.println("[Event] ES 事件索引同步失败: " + ex.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("events", savedEvents);
        response.put("inheritedCount", inheritedIds.size());
        response.put("totalArticles", result.totalArticles());
        response.put("clusteredArticles", result.clusteredArticles());
        response.put("unclusteredArticles", result.unclusteredArticles());
        return response;
    }

    @Override
    public PageResult<EventVO> listEvents(long pageNum, long pageSize, String category, String sortBy, String sortOrder) {
        Page<Event> page = new Page<>(pageNum, pageSize);
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<Event>()
                .eq(category != null && !category.isBlank(), Event::getCategory, category);
        if ("startTime".equals(sortBy)) {
            if (asc) wrapper.orderByAsc(Event::getStartTime);
            else wrapper.orderByDesc(Event::getStartTime);
        } else {
            if (asc) wrapper.orderByAsc(Event::getHotness);
            else wrapper.orderByDesc(Event::getHotness);
        }
        if (!"ADMIN".equals(UserContext.getRequired().role())) {
            wrapper.eq(Event::getUserId, UserContext.getRequired().userId());
        }
        Page<Event> result = eventMapper.selectPage(page, wrapper);

        // 批量查询每个事件的情感数据
        List<Long> eventIds = result.getRecords().stream().map(Event::getId).toList();
        Map<Long, Map<String, BigDecimal>> sentimentMap = new HashMap<>();
        if (!eventIds.isEmpty()) {
            List<EventArticle> allRelations = eventArticleMapper.selectList(
                    new LambdaQueryWrapper<EventArticle>().in(EventArticle::getEventId, eventIds)
            );
            Map<Long, List<Long>> eventCleanIds = allRelations.stream()
                    .collect(Collectors.groupingBy(EventArticle::getEventId,
                            Collectors.mapping(EventArticle::getCleanId, Collectors.toList())));

            for (var entry : eventCleanIds.entrySet()) {
                Long eid = entry.getKey();
                List<Long> cids = entry.getValue();
                if (cids.isEmpty()) continue;
                List<ArticleSentiment> sentiments = articleSentimentMapper.selectList(
                        new LambdaQueryWrapper<ArticleSentiment>().in(ArticleSentiment::getCleanId, cids)
                );
                int pos = 0, neg = 0, neu = 0;
                for (ArticleSentiment s : sentiments) {
                    switch (s.getSentiment()) {
                        case "POSITIVE" -> pos++;
                        case "NEGATIVE" -> neg++;
                        default -> neu++;
                    }
                }
                int total = Math.max(sentiments.size(), 1);
                Map<String, BigDecimal> m = new HashMap<>();
                m.put("pos", BigDecimal.valueOf(pos).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP));
                m.put("neg", BigDecimal.valueOf(neg).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP));
                m.put("neu", BigDecimal.valueOf(neu).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP));
                sentimentMap.put(eid, m);
            }
        }

        List<EventVO> records = result.getRecords().stream().map(e -> {
            Map<String, BigDecimal> sent = sentimentMap.get(e.getId());
            if (sent != null) {
                return EventVO.from(e, sent.get("pos"), sent.get("neg"), sent.get("neu"));
            }
            return EventVO.from(e);
        }).toList();
        return new PageResult<>(records, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public EventDetailVO getEvent(Long id) {
        Event event = eventMapper.selectById(id);
        if (event == null) {
            throw new EventNotFoundException("事件不存在: " + id);
        }

        List<Long> articleIds = eventArticleMapper.selectList(
                        new LambdaQueryWrapper<EventArticle>()
                                .eq(EventArticle::getEventId, id)
                ).stream()
                .map(EventArticle::getCleanId)
                .toList();

        return EventDetailVO.from(event, articleIds);
    }

    @Override
    public PageResult<EventVO> searchEvents(String keyword, long pageNum, long pageSize) {
        org.springframework.data.domain.Page<EventDocument> page =
                searchSyncService.searchEvents(keyword, (int) pageNum, (int) pageSize);
        List<EventVO> records = page.getContent().stream()
                .map(doc -> new EventVO(
                        doc.getId(),
                        doc.getTitle(),
                        doc.getKeywords(),
                        doc.getArticleCount(),
                        doc.getHotness() != null ? java.math.BigDecimal.valueOf(doc.getHotness()) : null,
                        doc.getLifecycle(),
                        doc.getCategory(),
                        doc.getStartTime(),
                        doc.getEndTime(),
                        doc.getCreateTime(),
                        null, null, null, null
                ))
                .toList();
        return new PageResult<>(records, page.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public List<SimilarEventResult> findSimilarEvents(String keywords, int topK) {
        List<EventSimilarHit> hits = searchSyncService.findSimilarEvents(keywords, topK);
        return hits.stream()
                .map(h -> SimilarEventResult.from(h.document(), h.score()))
                .toList();
    }

    @Override
    public Map<String, Object> forecastTrend(Long eventId, int periods) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new EventNotFoundException("事件不存在: " + eventId);
        }

        List<Map<String, Object>> dailyCounts = dailyCounts(eventId);

        // 只把近期活跃集群喂给预测模型，避开古早离群点
        List<Map<String, Object>> recentCounts = filterRecentCluster(dailyCounts);

        Map<String, Object> result = pythonIntelligenceClient.forecastTrend(recentCounts, periods);

        // 将完整历史数据返回前端用于可视化（含古早数据点），
        // 但模型拟合值 yhat 仅存在于近期集群的日期中
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modelHistorical = (List<Map<String, Object>>) result.get("historical");
        Map<String, Object> yhatByDate = new HashMap<>();
        if (modelHistorical != null) {
            for (Map<String, Object> h : modelHistorical) {
                Object yhat = h.get("yhat");
                if (yhat != null) {
                    yhatByDate.put((String) h.get("date"), yhat);
                }
            }
        }

        List<Map<String, Object>> fullHistorical = new ArrayList<>();
        for (Map<String, Object> d : dailyCounts) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", d.get("date"));
            item.put("count", d.get("count"));
            Object yhat = yhatByDate.get((String) d.get("date"));
            if (yhat != null) {
                item.put("yhat", yhat);
            }
            fullHistorical.add(item);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("eventId", eventId);
        response.put("eventTitle", event.getTitle());
        response.put("method", result.get("method"));
        response.put("historical", fullHistorical);
        response.put("forecast", result.get("forecast"));
        response.put("trend", result.get("trend"));
        response.put("note", result.get("note"));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEvent(Long id) {
        Event event = eventMapper.selectById(id);
        if (event == null) {
            throw new EventNotFoundException("事件不存在: " + id);
        }
        eventArticleMapper.delete(
                new LambdaQueryWrapper<EventArticle>().eq(EventArticle::getEventId, id)
        );
        eventMapper.deleteById(id);
    }

    @Override
    public List<EventVO> getMyFeedEvents(Long userId) {
        List<UserKeyword> keywords = userPreferenceService.listKeywords(userId);
        List<UserDomain> domains = userPreferenceService.listDomains(userId);

        if (keywords.isEmpty() && domains.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            for (UserKeyword kw : keywords) {
                w.or().like(Event::getTitle, kw.getKeyword())
                 .or().like(Event::getKeywords, kw.getKeyword());
            }
            for (UserDomain domain : domains) {
                w.or().like(Event::getCategory, domain.getDomainName());
            }
        });
        wrapper.orderByDesc(Event::getHotness);

        List<Event> events = eventMapper.selectList(wrapper);

        Set<String> keywordSet = keywords.stream().map(UserKeyword::getKeyword).collect(Collectors.toSet());
        Set<String> domainSet = domains.stream().map(UserDomain::getDomainName).collect(Collectors.toSet());

        return events.stream().map(e -> {
            boolean kwMatch = keywordSet.stream().anyMatch(kw ->
                    (e.getTitle() != null && e.getTitle().contains(kw)) ||
                    (e.getKeywords() != null && e.getKeywords().contains(kw)));
            boolean domMatch = domainSet.stream().anyMatch(d ->
                    e.getCategory() != null && e.getCategory().contains(d));
            String matchType = kwMatch && domMatch ? "both" : kwMatch ? "keyword" : "domain";
            return EventVO.from(e, matchType);
        }).toList();
    }

    @Override
    public Map<String, Object> fullReport(Long eventId) {
        // 1. 验证事件存在
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new EventNotFoundException("事件不存在: " + eventId);
        }

        // 2. 获取 cleanIds
        List<EventArticle> relations = eventArticleMapper.selectList(
                new LambdaQueryWrapper<EventArticle>()
                        .eq(EventArticle::getEventId, eventId)
        );
        List<Long> cleanIds = relations.stream().map(EventArticle::getCleanId).toList();

        // 3. 情感统计
        Map<String, Object> sentiment = new HashMap<>();
        if (!cleanIds.isEmpty()) {
            List<ArticleSentiment> sentiments = articleSentimentMapper.selectList(
                    new LambdaQueryWrapper<ArticleSentiment>()
                            .in(ArticleSentiment::getCleanId, cleanIds)
            );
            int pos = 0, neg = 0, neu = 0;
            for (ArticleSentiment s : sentiments) {
                switch (s.getSentiment()) {
                    case "POSITIVE" -> pos++;
                    case "NEGATIVE" -> neg++;
                    default -> neu++;
                }
            }
            int total = Math.max(sentiments.size(), 1);
            sentiment.put("positive", BigDecimal.valueOf(pos).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP));
            sentiment.put("negative", BigDecimal.valueOf(neg).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP));
            sentiment.put("neutral", BigDecimal.valueOf(neu).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP));
            sentiment.put("total", sentiments.size());
        } else {
            sentiment.put("positive", BigDecimal.ZERO);
            sentiment.put("negative", BigDecimal.ZERO);
            sentiment.put("neutral", BigDecimal.ZERO);
            sentiment.put("total", 0);
        }

        // 4. 平台分布 + 5. 文章列表
        List<Map<String, Object>> sourceDistribution = new ArrayList<>();
        List<Map<String, Object>> articleList = new ArrayList<>();
        List<Map<String, Object>> topKeywords = new ArrayList<>();
        if (!cleanIds.isEmpty()) {
            List<ArticleClean> articles = articleCleanMapper.selectList(
                    new LambdaQueryWrapper<ArticleClean>()
                            .in(ArticleClean::getId, cleanIds)
            );

            // 平台分布
            Map<String, Long> sourceCounts = articles.stream()
                    .collect(Collectors.groupingBy(
                            a -> a.getSourceName() != null && !a.getSourceName().isBlank()
                                    ? a.getSourceName() : "未知来源",
                            Collectors.counting()
                    ));
            sourceDistribution = sourceCounts.entrySet().stream()
                    .map(e -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("source", e.getKey());
                        item.put("count", e.getValue().intValue());
                        return item;
                    })
                    .toList();

            // 文章列表（仅返回基本信息）
            articleList = articles.stream()
                    .map(a -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", a.getId());
                        item.put("title", a.getTitle() != null ? a.getTitle() : "");
                        item.put("sourceName", a.getSourceName() != null ? a.getSourceName() : "");
                        item.put("publishedAt", a.getPublishedAt() != null ? a.getPublishedAt() : "");
                        return item;
                    })
                    .toList();

            // 高频关键词统计（基于文章 keywords 字段聚合词频）
            Map<String, Long> wordFreq = new HashMap<>();
            for (ArticleClean a : articles) {
                String kw = a.getKeywords();
                if (kw == null || kw.isBlank()) continue;
                for (String word : kw.split("[,，]")) {
                    String trimmed = word.trim();
                    if (!trimmed.isEmpty() && trimmed.length() >= 2) {
                        wordFreq.merge(trimmed, 1L, Long::sum);
                    }
                }
            }
            topKeywords = wordFreq.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20)
                    .map(e -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("word", e.getKey());
                        item.put("count", e.getValue().intValue());
                        return item;
                    })
                    .toList();
        }

        // 6. 每日趋势
        List<Map<String, Object>> dailyTrend = dailyCounts(eventId);

        // 7. 事件摘要（LLM + 降级）
        Map<String, Object> summary = new HashMap<>();
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event_title", event.getTitle() != null ? event.getTitle() : "");
            payload.put("event_keywords", event.getKeywords() != null ? event.getKeywords() : "");

            List<Map<String, Object>> pyArticles = new ArrayList<>();
            for (Map<String, Object> a : articleList.stream().limit(20).toList()) {
                Map<String, Object> pa = new HashMap<>();
                pa.put("title", a.get("title"));
                pa.put("published_at", a.get("publishedAt"));
                pa.put("source_name", a.get("sourceName"));

                // 尝试取 summary，如果有
                pa.put("summary", "");
                pyArticles.add(pa);
            }
            payload.put("articles", pyArticles);

            summary = pythonIntelligenceClient.getEventSummary(payload);
        } catch (Exception e) {
            summary.put("summary", "该事件为「" + (event.getTitle() != null ? event.getTitle() : "") + "」，"
                    + "关键词：" + (event.getKeywords() != null ? event.getKeywords() : "") + "，"
                    + "共涉及 " + event.getArticleCount() + " 篇报道。");
            summary.put("time", "");
            summary.put("location", "");
            summary.put("cause", "");
            summary.put("persons", "");
            summary.put("key_steps", "");
            summary.put("important_info", "");
            summary.put("method", "fallback");
        }

        // 8. 组装响应
        Map<String, Object> response = new HashMap<>();
        response.put("event", EventDetailVO.from(event, cleanIds));
        response.put("summary", summary);
        response.put("sentiment", sentiment);
        response.put("sourceDistribution", sourceDistribution);
        response.put("dailyTrend", dailyTrend);
        response.put("articles", articleList);
        response.put("topKeywords", topKeywords);
        return response;
    }

    /**
     * 统计指定事件的文章每日数量（基于 MySQL event_article + article_clean 表）
     */
    private List<Map<String, Object>> dailyCounts(Long eventId) {
        List<Long> cleanIds = eventArticleMapper.selectList(
                new LambdaQueryWrapper<EventArticle>()
                        .eq(EventArticle::getEventId, eventId)
        ).stream().map(EventArticle::getCleanId).toList();

        if (cleanIds.isEmpty()) return List.of();

        List<ArticleClean> articles = articleCleanMapper.selectList(
                new LambdaQueryWrapper<ArticleClean>()
                        .in(ArticleClean::getId, cleanIds)
                        .isNotNull(ArticleClean::getPublishedAt)
        );

        // 按日期分组统计
        Map<LocalDate, Long> dateCounts = new LinkedHashMap<>();
        for (ArticleClean a : articles) {
            String publishedAt = a.getPublishedAt();
            if (publishedAt == null || publishedAt.length() < 10) continue;
            try {
                LocalDate date = LocalDate.parse(publishedAt.substring(0, 10));
                dateCounts.merge(date, 1L, Long::sum);
            } catch (Exception ignored) {
            }
        }

        // 只返回有数据的日期（不做零值填充，避免古早文章拉出数千天空白）
        if (dateCounts.isEmpty()) return List.of();

        return dateCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("date", e.getKey().toString());
                    item.put("count", e.getValue().intValue());
                    return item;
                })
                .toList();
    }

    /**
     * 从最新的日期向前扫描，>90天间隙处断开，只保留近期活跃集群。
     * 用于趋势预测模型，避免古早数据点干扰拟合。
     */
    private List<Map<String, Object>> filterRecentCluster(List<Map<String, Object>> dailyCounts) {
        if (dailyCounts.size() <= 1) return dailyCounts;

        // 按日期排序
        List<Map<String, Object>> sorted = dailyCounts.stream()
                .sorted((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")))
                .toList();

        List<Map<String, Object>> cluster = new ArrayList<>();
        LocalDate prevDate = null;

        for (int i = sorted.size() - 1; i >= 0; i--) {
            Map<String, Object> item = sorted.get(i);
            String dateStr = (String) item.get("date");
            if (dateStr == null || dateStr.length() < 10) {
                cluster.add(item);
                continue;
            }

            LocalDate d;
            try { d = LocalDate.parse(dateStr.substring(0, 10)); }
            catch (Exception e) { cluster.add(item); continue; }

            if (prevDate == null) {
                cluster.add(item);
                prevDate = d;
                continue;
            }

            long gapDays = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(prevDate, d));
            if (gapDays > 90) break;

            cluster.add(item);
            prevDate = d;
        }

        Collections.reverse(cluster);
        return cluster;
    }

    /**
     * 解析日期时间字符串，支持多种格式：
     * ISO格式 2026-07-13T00:00:00、标准格式 2026-07-13 00:00:00、纯日期 2026-07-13
     */
    private LocalDateTime parseDateTime(String s) {
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e1) {
            try {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e2) {
                try {
                    return LocalDate.parse(s.substring(0, 10)).atStartOfDay();
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

}