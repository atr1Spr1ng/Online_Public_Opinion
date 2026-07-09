package com.bupt.publicopinion.event.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.analysis.client.PythonIntelligenceClient;
import com.bupt.publicopinion.analysis.entity.ArticleSentiment;
import com.bupt.publicopinion.analysis.exception.IntelligenceServiceException;
import com.bupt.publicopinion.analysis.mapper.ArticleSentimentMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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

    public EventServiceImpl(
            EventMapper eventMapper,
            EventArticleMapper eventArticleMapper,
            ArticleCleanMapper articleCleanMapper,
            ArticleSentimentMapper articleSentimentMapper,
            PythonIntelligenceClient pythonIntelligenceClient,
            SearchSyncService searchSyncService
    ) {
        this.eventMapper = eventMapper;
        this.eventArticleMapper = eventArticleMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.articleSentimentMapper = articleSentimentMapper;
        this.pythonIntelligenceClient = pythonIntelligenceClient;
        this.searchSyncService = searchSyncService;
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

        // 3. 清除旧事件（幂等）
        eventArticleMapper.delete(new LambdaQueryWrapper<>());
        eventMapper.delete(new LambdaQueryWrapper<>());
        searchSyncService.deleteAllEvents();

        // 4. 收集所有有效 cleanId
        Set<Long> validCleanIds = articles.stream()
                .map(ArticleClean::getId)
                .collect(Collectors.toSet());

        // 5. 分类（LDA + 关键词词典）
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

        // 6. 写入新事件
        List<EventVO> savedEvents = new ArrayList<>();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
                try {
                    event.setStartTime(LocalDateTime.parse(item.startTime(), dtf));
                } catch (Exception ignored) {
                }
            }
            if (!item.endTime().isEmpty()) {
                try {
                    event.setEndTime(LocalDateTime.parse(item.endTime(), dtf));
                } catch (Exception ignored) {
                }
            }

            eventMapper.insert(event);

            for (Long cleanId : item.articleIds()) {
                if (!validCleanIds.contains(cleanId)) continue;
                EventArticle ea = new EventArticle();
                ea.setEventId(event.getId());
                ea.setCleanId(cleanId);
                eventArticleMapper.insert(ea);
            }

            savedEvents.add(EventVO.from(event));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("events", savedEvents);
        response.put("totalArticles", result.totalArticles());
        response.put("clusteredArticles", result.clusteredArticles());
        response.put("unclusteredArticles", result.unclusteredArticles());
        return response;
    }

    @Override
    public PageResult<EventVO> listEvents(long pageNum, long pageSize, String category) {
        Page<Event> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<Event>()
                .eq(category != null && !category.isBlank(), Event::getCategory, category)
                .orderByDesc(Event::getHotness);
        Page<Event> result = eventMapper.selectPage(page, wrapper);
        List<EventVO> records = result.getRecords().stream().map(EventVO::from).toList();
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
                        doc.getCreateTime()
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

        Map<String, Object> result = pythonIntelligenceClient.forecastTrend(dailyCounts, periods);

        Map<String, Object> response = new HashMap<>();
        response.put("eventId", eventId);
        response.put("eventTitle", event.getTitle());
        response.put("method", result.get("method"));
        response.put("historical", result.get("historical"));
        response.put("forecast", result.get("forecast"));
        response.put("trend", result.get("trend"));
        response.put("note", result.get("note"));
        return response;
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

        // 排序并填充缺失日期
        if (dateCounts.isEmpty()) return List.of();

        List<LocalDate> sortedDates = new ArrayList<>(dateCounts.keySet());
        sortedDates.sort(LocalDate::compareTo);
        LocalDate start = sortedDates.get(0);
        LocalDate end = sortedDates.get(sortedDates.size() - 1);

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", d.toString());
            item.put("count", dateCounts.getOrDefault(d, 0L).intValue());
            result.add(item);
        }
        return result;
    }
}