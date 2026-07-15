package com.bupt.publicopinion.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.analysis.client.PythonIntelligenceClient;
import com.bupt.publicopinion.analysis.dto.SentimentRequest;
import com.bupt.publicopinion.analysis.entity.ArticleSentiment;
import com.bupt.publicopinion.analysis.exception.IntelligenceServiceException;
import com.bupt.publicopinion.analysis.mapper.ArticleSentimentMapper;
import com.bupt.publicopinion.analysis.service.AnalysisService;
import com.bupt.publicopinion.analysis.vo.SentimentResult;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.mapper.ArticleRawMapper;
import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.mapper.ArticleCleanMapper;
import com.bupt.publicopinion.task.entity.ProcessingTask;
import com.bupt.publicopinion.task.service.ProcessingTaskService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AnalysisServiceImpl implements AnalysisService {

    private final ArticleSentimentMapper articleSentimentMapper;
    private final ArticleCleanMapper articleCleanMapper;
    private final ArticleRawMapper articleRawMapper;
    private final PythonIntelligenceClient pythonIntelligenceClient;
    private final ProcessingTaskService processingTaskService;

    public AnalysisServiceImpl(
            ArticleSentimentMapper articleSentimentMapper,
            ArticleCleanMapper articleCleanMapper,
            ArticleRawMapper articleRawMapper,
            PythonIntelligenceClient pythonIntelligenceClient,
            ProcessingTaskService processingTaskService
    ) {
        this.articleSentimentMapper = articleSentimentMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.articleRawMapper = articleRawMapper;
        this.pythonIntelligenceClient = pythonIntelligenceClient;
        this.processingTaskService = processingTaskService;
    }

    @Override
    public SentimentResult analyzeSentiment(SentimentRequest request) {
        return analyzeSentimentInternal(request, normalizeMode(request.mode(), 1));
    }

    private SentimentResult analyzeSentimentInternal(SentimentRequest request, String mode) {
        ArticleClean clean = articleCleanMapper.selectById(request.cleanId());
        if (clean == null) {
            throw new IntelligenceServiceException("清洗后的文章不存在: " + request.cleanId());
        }

        // 删除旧结果，确保重新分析时不会重复
        articleSentimentMapper.delete(
                new LambdaQueryWrapper<ArticleSentiment>()
                        .eq(ArticleSentiment::getCleanId, request.cleanId())
        );

        SentimentResult result = pythonIntelligenceClient.analyzeSentiment(
                clean.getTitle(), clean.getContent(), mode
        );
        ArticleSentiment entity = saveSentimentResult(clean.getId(), result);
        return new SentimentResult(
                entity.getId(), entity.getCleanId(), entity.getSentiment(),
                entity.getPositiveScore(), entity.getNegativeScore(),
                entity.getConfidence(), entity.getDetailsJson(), entity.getCreateTime()
        );
    }

    @Override
    public ProcessingTask batchAnalyze(List<Long> cleanIds) {
        return batchAnalyze(cleanIds, null);
    }

    @Override
    public ProcessingTask batchAnalyze(List<Long> cleanIds, String mode) {
        List<Long> ids = normalizeIds(cleanIds);
        String resolvedMode = normalizeMode(mode, ids.size());
        ProcessingTask task = processingTaskService.createTask("SENTIMENT", "CLEAN_ARTICLE", ids.size());
        task.setMessage("分析模式：" + modeLabel(resolvedMode, ids.size()));
        final UserContext.UserContextInfo ctx = UserContext.get();
        processingTaskService.runAsync(task.getId(), ctx, taskId -> processBatchAnalyze(taskId, ids, resolvedMode));
        return task;
    }

    private void processBatchAnalyze(Long taskId, List<Long> cleanIds, String mode) {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicReference<String> lastMessage = new AtomicReference<>("");
        final UserContext.UserContextInfo ctx = UserContext.get();
        Semaphore sem = new Semaphore(5);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Long cleanId : cleanIds) {
            futures.add(CompletableFuture.runAsync(() -> {
                UserContext.set(ctx);
                boolean acquired = false;
                try {
                    sem.acquire();
                    acquired = true;
                    ArticleClean clean = articleCleanMapper.selectById(cleanId);
                    analyzeSentimentInternal(new SentimentRequest(cleanId, mode), mode);
                    processingTaskService.recordItem(taskId, cleanId, "CLEAN_ARTICLE",
                            clean != null ? clean.getTitle() : "文章 " + cleanId,
                            "SUCCESS", "");
                    int ok = success.incrementAndGet();
                    lastMessage.set("已分析文章 " + cleanId + "；模式：" + modeLabel(mode, cleanIds.size()));
                    processingTaskService.updateProgress(taskId, ok, failed.get(), lastMessage.get());
                } catch (Exception e) {
                    int fail = failed.incrementAndGet();
                    lastMessage.set("文章 " + cleanId + " 情感分析失败: " + e.getMessage() + "；模式：" + modeLabel(mode, cleanIds.size()));
                    processingTaskService.recordItem(taskId, cleanId, "CLEAN_ARTICLE",
                            resolveCleanTitle(cleanId), "FAILED", e.getMessage());
                    processingTaskService.updateProgress(taskId, success.get(), fail, lastMessage.get());
                } finally {
                    if (acquired) sem.release();
                    UserContext.clear();
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        processingTaskService.finish(taskId, success.get(), failed.get(),
                (failed.get() > 0 ? "情感分析完成，部分文章失败" : "情感分析完成")
                        + "；模式：" + modeLabel(mode, cleanIds.size()));
    }

    @Override
    public ArticleSentiment getSentimentResult(Long id) {
        ArticleSentiment sentiment = articleSentimentMapper.selectById(id);
        if (sentiment == null) {
            throw new IntelligenceServiceException("情感分析结果不存在: " + id);
        }
        return sentiment;
    }

    @Override
    public PageResult<ArticleSentiment> listSentimentResults(long pageNum, long pageSize, String sentiment) {
        Page<ArticleSentiment> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ArticleSentiment> wrapper = new LambdaQueryWrapper<>();
        if (sentiment != null && !sentiment.isBlank()) {
            wrapper.eq(ArticleSentiment::getSentiment, sentiment.toUpperCase());
        }
        wrapper.orderByDesc(ArticleSentiment::getCreateTime);
        if (!"ADMIN".equals(UserContext.getRequired().role())) {
            wrapper.eq(ArticleSentiment::getUserId, UserContext.getRequired().userId());
        }
        Page<ArticleSentiment> result = articleSentimentMapper.selectPage(page, wrapper);

        // 批量查询关联的清洗文章和原文URL
        List<Long> cleanIds = result.getRecords().stream().map(ArticleSentiment::getCleanId).toList();
        if (!cleanIds.isEmpty()) {
            List<ArticleClean> cleans = articleCleanMapper.selectBatchIds(cleanIds);
            Map<Long, ArticleClean> cleanMap = new HashMap<>();
            for (ArticleClean c : cleans) {
                cleanMap.put(c.getId(), c);
            }
            List<Long> rawIds = cleans.stream().map(ArticleClean::getRawId).filter(r -> r != null).toList();
            Map<Long, String> urlMap = new HashMap<>();
            if (!rawIds.isEmpty()) {
                List<ArticleRaw> raws = articleRawMapper.selectBatchIds(rawIds);
                for (ArticleRaw r : raws) {
                    urlMap.put(r.getId(), r.getOriginalUrl());
                }
            }
            for (ArticleSentiment s : result.getRecords()) {
                ArticleClean clean = cleanMap.get(s.getCleanId());
                if (clean != null) {
                    s.setTitle(clean.getTitle());
                    s.setOriginalUrl(urlMap.get(clean.getRawId()));
                }
            }
        }

        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public void deleteSentimentResult(Long id) {
        ArticleSentiment sentiment = articleSentimentMapper.selectById(id);
        if (sentiment == null) {
            throw new IntelligenceServiceException("情感分析结果不存在: " + id);
        }
        articleSentimentMapper.deleteById(id);
    }

    private ArticleSentiment saveSentimentResult(Long cleanId, SentimentResult result) {
        ArticleSentiment entity = new ArticleSentiment();
        entity.setCleanId(cleanId);
        entity.setSentiment(result.sentiment());
        entity.setPositiveScore(result.positiveScore());
        entity.setNegativeScore(result.negativeScore());
        entity.setConfidence(result.confidence());
        entity.setDetailsJson(result.detailsJson());
        entity.setUserId(UserContext.getRequired().userId());
        articleSentimentMapper.insert(entity);
        return entity;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return new ArrayList<>(new LinkedHashSet<>(ids.stream()
                .filter(id -> id != null && id > 0)
                .toList()));
    }

    private String resolveCleanTitle(Long cleanId) {
        ArticleClean clean = articleCleanMapper.selectById(cleanId);
        if (clean == null) return "文章 " + cleanId;
        return clean.getTitle() != null && !clean.getTitle().isBlank() ? clean.getTitle() : "文章 " + cleanId;
    }

    private String normalizeMode(String mode, int count) {
        if ("fast".equalsIgnoreCase(mode)) return "fast";
        if ("accurate".equalsIgnoreCase(mode) || "llm".equalsIgnoreCase(mode)) return "accurate";
        if ("auto".equalsIgnoreCase(mode) || mode == null || mode.isBlank()) {
            return count > 0 && count <= 50 ? "accurate" : "auto";
        }
        return "fast";
    }

    private String modeLabel(String mode, int count) {
        if ("accurate".equals(mode)) return "精准模式";
        return "快速模式";
    }
}
