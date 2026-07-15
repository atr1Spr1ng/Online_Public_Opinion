package com.bupt.publicopinion.event.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.analysis.client.PythonIntelligenceClient;
import com.bupt.publicopinion.analysis.entity.ArticleSentiment;
import com.bupt.publicopinion.analysis.exception.IntelligenceServiceException;
import com.bupt.publicopinion.analysis.mapper.ArticleSentimentMapper;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.mapper.ArticleRawMapper;
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
import com.bupt.publicopinion.task.entity.ProcessingTask;
import com.bupt.publicopinion.task.service.ProcessingTaskService;
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
    private static final int DEFAULT_CLUSTER_WINDOW_DAYS = 30;
    private static final int MAX_CLUSTER_WINDOW_DAYS = 3650;
    private static final int DEFAULT_MIN_CLUSTER_SIZE = 3;

    private final EventMapper eventMapper;
    private final EventArticleMapper eventArticleMapper;
    private final ArticleCleanMapper articleCleanMapper;
    private final ArticleSentimentMapper articleSentimentMapper;
    private final ArticleRawMapper articleRawMapper;
    private final PythonIntelligenceClient pythonIntelligenceClient;
    private final SearchSyncService searchSyncService;
    private final UserPreferenceService userPreferenceService;
    private final ProcessingTaskService processingTaskService;

    public EventServiceImpl(
            EventMapper eventMapper,
            EventArticleMapper eventArticleMapper,
            ArticleCleanMapper articleCleanMapper,
            ArticleSentimentMapper articleSentimentMapper,
            ArticleRawMapper articleRawMapper,
            PythonIntelligenceClient pythonIntelligenceClient,
            SearchSyncService searchSyncService,
            UserPreferenceService userPreferenceService,
            ProcessingTaskService processingTaskService
    ) {
        this.eventMapper = eventMapper;
        this.eventArticleMapper = eventArticleMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.articleSentimentMapper = articleSentimentMapper;
        this.articleRawMapper = articleRawMapper;
        this.pythonIntelligenceClient = pythonIntelligenceClient;
        this.searchSyncService = searchSyncService;
        this.userPreferenceService = userPreferenceService;
        this.processingTaskService = processingTaskService;
    }

    @Override
    public ProcessingTask clusterAsync(double threshold, Integer days, Integer minClusterSize) {
        int windowDays = normalizeClusterWindowDays(days);
        int normalizedMinClusterSize = normalizeMinClusterSize(minClusterSize);
        int total = estimateClusterArticleCount(windowDays);
        ProcessingTask task = processingTaskService.createTask("EVENT_CLUSTER", "EVENT", total);
        UserContext.UserContextInfo ctx = UserContext.get();
        processingTaskService.runAsync(task.getId(), ctx, taskId -> {
            Map<String, Object> result = clusterAndSave(threshold, windowDays, normalizedMinClusterSize);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            if (success) {
                int clustered = result.get("clusteredArticles") instanceof Number n ? n.intValue() : total;
                int unclustered = result.get("unclusteredArticles") instanceof Number n ? n.intValue() : Math.max(total - clustered, 0);
                processingTaskService.finish(taskId, total, 0,
                        "事件聚类完成，范围：" + clusterWindowLabel(windowDays)
                                + "；最小成簇 " + normalizedMinClusterSize + " 篇"
                                + "；已聚类 " + clustered + " 篇，未成簇 " + unclustered + " 篇");
            } else {
                String message = String.valueOf(result.getOrDefault("message", "事件聚类失败"));
                processingTaskService.recordItem(taskId, null, "STAGE", "事件聚类执行", "FAILED", message);
                processingTaskService.fail(taskId, 0, total, message);
            }
        });
        return task;
    }

    private int estimateClusterArticleCount(int windowDays) {
        Long count = articleCleanMapper.selectCount(buildClusterArticleQuery(windowDays));
        return (int) Math.min(count != null ? count : 0, MAX_ARTICLES_FOR_CLUSTERING);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> clusterAndSave(double threshold, Integer days, Integer minClusterSize) {
        int windowDays = normalizeClusterWindowDays(days);
        int normalizedMinClusterSize = normalizeMinClusterSize(minClusterSize);
        // 1. 取文章，限流
        List<ArticleClean> articles = articleCleanMapper.selectList(
                buildClusterArticleQuery(windowDays)
                        .orderByDesc(ArticleClean::getPublishedAt)
                        .orderByDesc(ArticleClean::getCreateTime)
                        .last("LIMIT " + MAX_ARTICLES_FOR_CLUSTERING)
        );

        List<Map<String, Object>> articleItems = new ArrayList<>();
        for (ArticleClean a : articles) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("title", a.getTitle() != null ? a.getTitle() : "");
            item.put("keywords", a.getKeywords() != null ? a.getKeywords() : "");
            item.put("summary", a.getSummary() != null ? a.getSummary() : "");
            item.put("published_at", a.getPublishedAt() != null ? a.getPublishedAt()
                    : a.getCreateTime() != null ? a.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            articleItems.add(item);
        }

        // 2. 加载现有事件，传给 Python 做增量聚类
        List<Event> oldEvents = eventMapper.selectList(new LambdaQueryWrapper<>());
        List<PythonIntelligenceClient.ExistingEventInfo> existingEvents = new ArrayList<>();
        for (Event oe : oldEvents) {
            List<EventArticle> eas = eventArticleMapper.selectList(
                    new LambdaQueryWrapper<EventArticle>().eq(EventArticle::getEventId, oe.getId())
            );
            List<Long> articleIds = eas.stream().map(EventArticle::getCleanId).toList();
            List<String> keywords = oe.getKeywords() != null
                    ? List.of(oe.getKeywords().split(","))
                    : List.of();
            existingEvents.add(new PythonIntelligenceClient.ExistingEventInfo(
                    oe.getId(), oe.getTitle(), keywords, articleIds));
        }

        // 3. 调 Python 增量聚类
        PythonIntelligenceClient.ClusterResult result;
        try {
            result = pythonIntelligenceClient.incrementalClusterEvents(
                    articleItems, existingEvents, threshold, 0.65, normalizedMinClusterSize);
        } catch (IntelligenceServiceException e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("message", e.getMessage());
            errorResult.put("events", List.of());
            return errorResult;
        }

        // 4. 收集所有有效 cleanId
        Set<Long> validCleanIds = articles.stream()
                .map(ArticleClean::getId)
                .collect(Collectors.toSet());

        // 5. 分类（LLM + 关键词词典）——仅对新事件分类
        Map<Integer, String> categoryMap = new HashMap<>();
        List<PythonIntelligenceClient.EventClusterItem> newEvents = result.events().stream()
                .filter(e -> !e.isExisting()).toList();
        if (!newEvents.isEmpty()) {
            try {
                List<Map<String, Object>> eventItems = new ArrayList<>();
                for (int i = 0; i < newEvents.size(); i++) {
                    var item = newEvents.get(i);
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
                    if (idx < newEvents.size()) {
                        categoryMap.put(idx, cat != null ? cat : "其他");
                    }
                }
            } catch (Exception e) {
                System.err.println("[Topic] 主题分类失败: " + e.getMessage());
            }
        }

        // 6. 处理增量聚类结果
        List<EventVO> savedEvents = new ArrayList<>();
        Long userId = UserContext.getRequired().userId();
        int newEventIdx = 0;

        for (PythonIntelligenceClient.EventClusterItem item : result.events()) {
            Event event = new Event();
            event.setTitle(item.title());
            event.setKeywords(String.join(",", item.keywords()));
            event.setArticleCount(item.articleCount());
            event.setHotness(BigDecimal.valueOf(item.hotness()));
            event.setLifecycle(item.lifecycle());

            if (!item.startTime().isEmpty()) {
                event.setStartTime(parseDateTime(item.startTime()));
            }
            if (!item.endTime().isEmpty()) {
                event.setEndTime(parseDateTime(item.endTime()));
            }

            event.setUserId(userId);

            if (item.isExisting()) {
                // 已有事件：Python 返回的 event_id 即数据库 ID，直接更新
                long existingId = item.eventId();
                event.setId(existingId);
                String oldCategory = oldEvents.stream()
                        .filter(oe -> oe.getId().equals(existingId))
                        .findFirst().map(Event::getCategory).orElse("其他");
                event.setCategory(oldCategory);
                eventMapper.updateById(event);

                // 重建 event_article 关联
                eventArticleMapper.delete(
                        new LambdaQueryWrapper<EventArticle>().eq(EventArticle::getEventId, existingId)
                );
                for (Long cleanId : item.articleIds()) {
                    if (!validCleanIds.contains(cleanId)) continue;
                    EventArticle ea = new EventArticle();
                    ea.setEventId(existingId);
                    ea.setCleanId(cleanId);
                    eventArticleMapper.insert(ea);
                }
                savedEvents.add(EventVO.from(event));
            } else {
                // 新事件：插入
                event.setCategory(categoryMap.getOrDefault(newEventIdx, "其他"));
                eventMapper.insert(event);
                newEventIdx++;

                for (Long cleanId : item.articleIds()) {
                    if (!validCleanIds.contains(cleanId)) continue;
                    EventArticle ea = new EventArticle();
                    ea.setEventId(event.getId());
                    ea.setCleanId(cleanId);
                    eventArticleMapper.insert(ea);
                }
                savedEvents.add(EventVO.from(event));
            }
        }

        int removedSmallEvents = cleanupSmallEvents(userId, normalizedMinClusterSize);
        int removedBoilerplateEvents = cleanupBoilerplateEvents(userId);

        // 7. 同步事件到 ES
        List<Event> allCurrentEvents = eventMapper.selectList(new LambdaQueryWrapper<>());
        try {
            searchSyncService.deleteAllEvents();
            searchSyncService.indexEvents(allCurrentEvents);
        } catch (Exception ex) {
            System.err.println("[Event] ES 事件索引同步失败: " + ex.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("events", savedEvents);
        response.put("inheritedCount", (int) result.events().stream().filter(PythonIntelligenceClient.EventClusterItem::isExisting).count());
        response.put("totalArticles", result.totalArticles());
        response.put("clusteredArticles", result.clusteredArticles());
        response.put("unclusteredArticles", result.unclusteredArticles());
        response.put("windowDays", windowDays);
        response.put("minClusterSize", normalizedMinClusterSize);
        response.put("removedSmallEvents", removedSmallEvents);
        response.put("removedBoilerplateEvents", removedBoilerplateEvents);
        return response;
    }

    private LambdaQueryWrapper<ArticleClean> buildClusterArticleQuery(int windowDays) {
        LambdaQueryWrapper<ArticleClean> wrapper = new LambdaQueryWrapper<ArticleClean>()
                .isNotNull(ArticleClean::getKeywords)
                .ne(ArticleClean::getKeywords, "")
                .isNotNull(ArticleClean::getPublishedAt)
                .ne(ArticleClean::getPublishedAt, "")
                .eq(ArticleClean::getStatus, "CLEANED");
        if (windowDays > 0) {
            String startDate = LocalDate.now().minusDays(windowDays).toString();
            wrapper.ge(ArticleClean::getPublishedAt, startDate);
        }
        return wrapper;
    }

    private int normalizeClusterWindowDays(Integer days) {
        if (days == null) {
            return DEFAULT_CLUSTER_WINDOW_DAYS;
        }
        if (days <= 0) {
            return 0;
        }
        return Math.min(days, MAX_CLUSTER_WINDOW_DAYS);
    }

    private int normalizeMinClusterSize(Integer minClusterSize) {
        if (minClusterSize == null) {
            return DEFAULT_MIN_CLUSTER_SIZE;
        }
        return Math.max(2, Math.min(minClusterSize, 20));
    }

    private int cleanupSmallEvents(Long userId, int minClusterSize) {
        List<Event> smallEvents = eventMapper.selectList(
                new LambdaQueryWrapper<Event>()
                        .eq(Event::getUserId, userId)
                        .lt(Event::getArticleCount, minClusterSize)
        );
        int removed = 0;
        for (Event event : smallEvents) {
            eventArticleMapper.delete(new LambdaQueryWrapper<EventArticle>().eq(EventArticle::getEventId, event.getId()));
            eventMapper.deleteById(event.getId());
            removed++;
        }
        return removed;
    }

    private int cleanupBoilerplateEvents(Long userId) {
        List<Event> events = eventMapper.selectList(
                new LambdaQueryWrapper<Event>()
                        .eq(Event::getUserId, userId)
        );
        int removed = 0;
        for (Event event : events) {
            String text = ((event.getTitle() != null ? event.getTitle() : "") + " "
                    + (event.getKeywords() != null ? event.getKeywords() : "")).toLowerCase();
            int hits = 0;
            String[] terms = {"copyright", "rights", "版权所有", "授权", "刊用", "务经", "chinanews", "sina"};
            for (String term : terms) {
                if (text.contains(term)) hits++;
            }
            if (hits >= 3) {
                eventArticleMapper.delete(new LambdaQueryWrapper<EventArticle>().eq(EventArticle::getEventId, event.getId()));
                eventMapper.deleteById(event.getId());
                removed++;
            }
        }
        return removed;
    }

    private String clusterWindowLabel(int days) {
        return days <= 0 ? "全部历史" : "最近 " + days + " 天";
    }

    private String resolveArticleSourceName(ArticleClean article, Map<Long, String> rawSourceMap) {
        if (article.getSourceName() != null && !article.getSourceName().isBlank()
                && !"未知新闻源".equals(article.getSourceName())
                && !"未知来源".equals(article.getSourceName())) {
            return article.getSourceName();
        }
        String rawSource = rawSourceMap.get(article.getRawId());
        if (rawSource != null && !rawSource.isBlank()) {
            return rawSource;
        }
        return "未知来源";
    }

    private String previewText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "……";
    }

    private boolean isMeaningfulKeyword(String word) {
        if (word == null) return false;
        String value = word.trim();
        if (value.length() < 2) return false;
        if (value.matches("\\d+")) return false;
        if (value.matches("\\d{1,4}[年月日号点时分秒]?")) return false;
        if (value.matches("\\d+(\\.\\d+)?%")) return false;
        if (value.matches("[一二三四五六七八九十百千万亿]+[年月日号点时分秒]?")) return false;
        return !Set.of(
                "一个", "一些", "一种", "这个", "那个", "这些", "那些", "进行", "表示", "相关", "记者",
                "报道", "消息", "目前", "今日", "昨日", "近日", "今天", "昨天", "明天", "时候", "方面",
                "情况", "问题", "工作", "新华社", "央视网", "人民网", "中新网"
        ).contains(value);
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
        searchSyncService.deleteEvent(id);
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

            // 批量查询原始文章信息，用于来源回退与原文链接
            List<Long> rawIds = articles.stream()
                    .map(ArticleClean::getRawId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
            Map<Long, String> urlMap = new HashMap<>();
            Map<Long, String> rawSourceMap = new HashMap<>();
            if (!rawIds.isEmpty()) {
                List<ArticleRaw> rawArticles = articleRawMapper.selectList(
                        new LambdaQueryWrapper<ArticleRaw>()
                                .select(ArticleRaw::getId, ArticleRaw::getOriginalUrl, ArticleRaw::getSourceName)
                                .in(ArticleRaw::getId, rawIds)
                );
                for (ArticleRaw r : rawArticles) {
                    if (r.getOriginalUrl() != null) {
                        urlMap.put(r.getId(), r.getOriginalUrl());
                    }
                    if (r.getSourceName() != null && !r.getSourceName().isBlank()) {
                        rawSourceMap.put(r.getId(), r.getSourceName());
                    }
                }
            }

            // 平台分布
            Map<String, Long> sourceCounts = articles.stream()
                    .collect(Collectors.groupingBy(
                            a -> resolveArticleSourceName(a, rawSourceMap),
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

            // 文章列表
            articleList = articles.stream()
                    .map(a -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", a.getId());
                        item.put("title", a.getTitle() != null ? a.getTitle() : "");
                        item.put("sourceName", resolveArticleSourceName(a, rawSourceMap));
                        item.put("publishedAt", a.getPublishedAt() != null ? a.getPublishedAt() : "");
                        item.put("originalUrl", urlMap.getOrDefault(a.getRawId(), ""));
                        item.put("summary", a.getSummary() != null ? a.getSummary() : "");
                        item.put("contentPreview", previewText(a.getContent(), 1200));
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
                    if (isMeaningfulKeyword(trimmed)) {
                        wordFreq.merge(trimmed, 1L, Long::sum);
                    }
                }
            }
            topKeywords = wordFreq.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
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
