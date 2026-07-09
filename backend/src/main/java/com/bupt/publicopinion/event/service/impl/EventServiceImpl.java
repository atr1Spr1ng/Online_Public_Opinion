package com.bupt.publicopinion.event.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.analysis.client.PythonIntelligenceClient;
import com.bupt.publicopinion.analysis.exception.IntelligenceServiceException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final PythonIntelligenceClient pythonIntelligenceClient;

    public EventServiceImpl(
            EventMapper eventMapper,
            EventArticleMapper eventArticleMapper,
            ArticleCleanMapper articleCleanMapper,
            PythonIntelligenceClient pythonIntelligenceClient
    ) {
        this.eventMapper = eventMapper;
        this.eventArticleMapper = eventArticleMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.pythonIntelligenceClient = pythonIntelligenceClient;
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
}
