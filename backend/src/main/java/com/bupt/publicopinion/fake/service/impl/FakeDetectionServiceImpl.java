package com.bupt.publicopinion.fake.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.analysis.client.PythonIntelligenceClient;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.mapper.ArticleRawMapper;
import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.mapper.ArticleCleanMapper;
import com.bupt.publicopinion.fake.dto.FakeDetectionRequest;
import com.bupt.publicopinion.fake.entity.ArticleFakeDetection;
import com.bupt.publicopinion.fake.exception.FakeDetectionException;
import com.bupt.publicopinion.fake.mapper.ArticleFakeDetectionMapper;
import com.bupt.publicopinion.fake.service.FakeDetectionService;
import com.bupt.publicopinion.fake.vo.FakeDetectionResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@Service
public class FakeDetectionServiceImpl implements FakeDetectionService {

    private final ArticleFakeDetectionMapper articleFakeDetectionMapper;
    private final ArticleCleanMapper articleCleanMapper;
    private final ArticleRawMapper articleRawMapper;
    private final PythonIntelligenceClient pythonIntelligenceClient;

    public FakeDetectionServiceImpl(
            ArticleFakeDetectionMapper articleFakeDetectionMapper,
            ArticleCleanMapper articleCleanMapper,
            ArticleRawMapper articleRawMapper,
            PythonIntelligenceClient pythonIntelligenceClient
    ) {
        this.articleFakeDetectionMapper = articleFakeDetectionMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.articleRawMapper = articleRawMapper;
        this.pythonIntelligenceClient = pythonIntelligenceClient;
    }

    @Override
    public FakeDetectionResult detect(FakeDetectionRequest request) {
        ArticleClean clean = articleCleanMapper.selectById(request.cleanId());
        if (clean == null) {
            throw new FakeDetectionException("清洗后的文章不存在: " + request.cleanId());
        }

        // 删除旧结果，确保重新检测时不会重复
        articleFakeDetectionMapper.delete(
                new LambdaQueryWrapper<ArticleFakeDetection>()
                        .eq(ArticleFakeDetection::getCleanId, request.cleanId())
        );

        PythonIntelligenceClient.FakeDetectionResult pythonResult =
                pythonIntelligenceClient.detectFake(clean.getTitle(), clean.getContent());

        ArticleFakeDetection entity = saveResult(clean.getId(), pythonResult);
        String originalUrl = null;
        if (clean.getRawId() != null) {
            ArticleRaw raw = articleRawMapper.selectById(clean.getRawId());
            if (raw != null) originalUrl = raw.getOriginalUrl();
        }
        return toVO(entity, clean.getTitle(), originalUrl);
    }

    @Override
    public List<FakeDetectionResult> batchDetect(List<Long> cleanIds) {
        List<FakeDetectionResult> results = new ArrayList<>();
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
                    FakeDetectionResult r = detect(new FakeDetectionRequest(cleanId));
                    synchronized (results) { results.add(r); }
                } catch (Exception e) {
                    synchronized (results) { results.add(FakeDetectionResult.failed(cleanId, e.getMessage())); }
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
    public FakeDetectionResult getResult(Long id) {
        ArticleFakeDetection entity = articleFakeDetectionMapper.selectById(id);
        if (entity == null) {
            throw new FakeDetectionException("虚假检测结果不存在: " + id);
        }
        return toVOWithClean(entity);
    }

    @Override
    public PageResult<FakeDetectionResult> listResults(long pageNum, long pageSize, Boolean isFake) {
        Page<ArticleFakeDetection> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ArticleFakeDetection> wrapper = new LambdaQueryWrapper<>();
        if (isFake != null) {
            wrapper.eq(ArticleFakeDetection::getIsFake, isFake ? 1 : 0);
        }
        wrapper.orderByDesc(ArticleFakeDetection::getCreateTime);
        if (!"ADMIN".equals(UserContext.getRequired().role())) {
            wrapper.eq(ArticleFakeDetection::getUserId, UserContext.getRequired().userId());
        }
        Page<ArticleFakeDetection> result = articleFakeDetectionMapper.selectPage(page, wrapper);

        // 批量查询关联的清洗文章和原文URL
        List<Long> cleanIds = result.getRecords().stream().map(ArticleFakeDetection::getCleanId).toList();
        Map<Long, ArticleClean> cleanMap = new HashMap<>();
        Map<Long, String> urlMap = new HashMap<>();
        if (!cleanIds.isEmpty()) {
            List<ArticleClean> cleans = articleCleanMapper.selectBatchIds(cleanIds);
            for (ArticleClean c : cleans) {
                cleanMap.put(c.getId(), c);
            }
            List<Long> rawIds = cleans.stream().map(ArticleClean::getRawId).filter(r -> r != null).toList();
            if (!rawIds.isEmpty()) {
                List<ArticleRaw> raws = articleRawMapper.selectBatchIds(rawIds);
                for (ArticleRaw r : raws) {
                    urlMap.put(r.getId(), r.getOriginalUrl());
                }
            }
        }

        List<FakeDetectionResult> records = result.getRecords().stream().map(e -> {
            ArticleClean clean = cleanMap.get(e.getCleanId());
            String title = clean != null ? clean.getTitle() : null;
            String originalUrl = clean != null ? urlMap.get(clean.getRawId()) : null;
            return toVO(e, title, originalUrl);
        }).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public void deleteFakeResult(Long id) {
        ArticleFakeDetection entity = articleFakeDetectionMapper.selectById(id);
        if (entity == null) {
            throw new FakeDetectionException("虚假检测结果不存在: " + id);
        }
        articleFakeDetectionMapper.deleteById(id);
    }

    private FakeDetectionResult toVOWithClean(ArticleFakeDetection entity) {
        String title = null;
        String originalUrl = null;
        ArticleClean clean = articleCleanMapper.selectById(entity.getCleanId());
        if (clean != null) {
            title = clean.getTitle();
            if (clean.getRawId() != null) {
                ArticleRaw raw = articleRawMapper.selectById(clean.getRawId());
                if (raw != null) originalUrl = raw.getOriginalUrl();
            }
        }
        return toVO(entity, title, originalUrl);
    }

    private ArticleFakeDetection saveResult(Long cleanId, PythonIntelligenceClient.FakeDetectionResult result) {
        ArticleFakeDetection entity = new ArticleFakeDetection();
        entity.setCleanId(cleanId);
        entity.setFakeScore(BigDecimal.valueOf(result.fakeScore()));
        entity.setIsFake(result.isFake() ? 1 : 0);
        entity.setDetectionMethod(result.detectionMethod());
        entity.setFeaturesJson(result.featuresJson());
        entity.setDetails(result.details());
        entity.setUserId(UserContext.getRequired().userId());
        articleFakeDetectionMapper.insert(entity);
        return entity;
    }

    private FakeDetectionResult toVO(ArticleFakeDetection entity, String title, String originalUrl) {
        return new FakeDetectionResult(
                entity.getId(),
                entity.getCleanId(),
                entity.getFakeScore(),
                entity.getIsFake() != null && entity.getIsFake() == 1,
                entity.getDetectionMethod(),
                entity.getFeaturesJson(),
                entity.getDetails(),
                entity.getCreateTime(),
                title,
                originalUrl
        );
    }
}
