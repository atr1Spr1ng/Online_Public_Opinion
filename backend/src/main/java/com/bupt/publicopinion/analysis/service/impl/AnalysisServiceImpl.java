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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@Service
public class AnalysisServiceImpl implements AnalysisService {

    private final ArticleSentimentMapper articleSentimentMapper;
    private final ArticleCleanMapper articleCleanMapper;
    private final ArticleRawMapper articleRawMapper;
    private final PythonIntelligenceClient pythonIntelligenceClient;

    public AnalysisServiceImpl(
            ArticleSentimentMapper articleSentimentMapper,
            ArticleCleanMapper articleCleanMapper,
            ArticleRawMapper articleRawMapper,
            PythonIntelligenceClient pythonIntelligenceClient
    ) {
        this.articleSentimentMapper = articleSentimentMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.articleRawMapper = articleRawMapper;
        this.pythonIntelligenceClient = pythonIntelligenceClient;
    }

    @Override
    public SentimentResult analyzeSentiment(SentimentRequest request) {
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
                clean.getTitle(), clean.getContent()
        );
        ArticleSentiment entity = saveSentimentResult(clean.getId(), result);
        return new SentimentResult(
                entity.getId(), entity.getCleanId(), entity.getSentiment(),
                entity.getPositiveScore(), entity.getNegativeScore(),
                entity.getConfidence(), entity.getDetailsJson(), entity.getCreateTime()
        );
    }

    @Override
    public List<SentimentResult> batchAnalyze(List<Long> cleanIds) {
        List<SentimentResult> results = new ArrayList<>();
        final UserContext.UserContextInfo ctx = UserContext.get();
        Semaphore sem = new Semaphore(5);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Long cleanId : cleanIds) {
            futures.add(CompletableFuture.runAsync(() -> {
                UserContext.set(ctx);
                try {
                    sem.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    SentimentResult r = analyzeSentiment(new SentimentRequest(cleanId));
                    synchronized (results) { results.add(r); }
                } catch (Exception e) {
                    synchronized (results) { results.add(SentimentResult.failed(cleanId, e.getMessage())); }
                } finally {
                    sem.release();
                    UserContext.clear();
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return results;
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
}
