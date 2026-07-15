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
import com.bupt.publicopinion.task.entity.ProcessingTask;
import com.bupt.publicopinion.task.service.ProcessingTaskService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
public class FakeDetectionServiceImpl implements FakeDetectionService {

    private final ArticleFakeDetectionMapper articleFakeDetectionMapper;
    private final ArticleCleanMapper articleCleanMapper;
    private final ArticleRawMapper articleRawMapper;
    private final PythonIntelligenceClient pythonIntelligenceClient;
    private final ProcessingTaskService processingTaskService;

    public FakeDetectionServiceImpl(
            ArticleFakeDetectionMapper articleFakeDetectionMapper,
            ArticleCleanMapper articleCleanMapper,
            ArticleRawMapper articleRawMapper,
            PythonIntelligenceClient pythonIntelligenceClient,
            ProcessingTaskService processingTaskService
    ) {
        this.articleFakeDetectionMapper = articleFakeDetectionMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.articleRawMapper = articleRawMapper;
        this.pythonIntelligenceClient = pythonIntelligenceClient;
        this.processingTaskService = processingTaskService;
    }

    @Override
    public FakeDetectionResult detect(FakeDetectionRequest request) {
        return detectInternal(request, normalizeMode(request.mode(), 1));
    }

    private FakeDetectionResult detectInternal(FakeDetectionRequest request, String mode) {
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
                pythonIntelligenceClient.detectFake(clean.getTitle(), clean.getContent(), mode);

        ArticleFakeDetection entity = saveResult(clean.getId(), pythonResult);
        String originalUrl = null;
        if (clean.getRawId() != null) {
            ArticleRaw raw = articleRawMapper.selectById(clean.getRawId());
            if (raw != null) originalUrl = raw.getOriginalUrl();
        }
        return toVO(entity, clean.getTitle(), originalUrl);
    }

    @Override
    public ProcessingTask batchDetect(List<Long> cleanIds) {
        return batchDetect(cleanIds, null);
    }

    @Override
    public ProcessingTask batchDetect(List<Long> cleanIds, String mode) {
        List<Long> ids = normalizeIds(cleanIds);
        String resolvedMode = normalizeMode(mode, ids.size());
        ProcessingTask task = processingTaskService.createTask("FAKE_DETECT", "CLEAN_ARTICLE", ids.size());
        task.setMessage("检测模式：" + modeLabel(resolvedMode, ids.size()));
        final UserContext.UserContextInfo ctx = UserContext.get();
        processingTaskService.runAsync(task.getId(), ctx, taskId -> processBatchDetect(taskId, ids, resolvedMode));
        return task;
    }

    private void processBatchDetect(Long taskId, List<Long> cleanIds, String mode) {
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
                    detectInternal(new FakeDetectionRequest(cleanId, mode), mode);
                    processingTaskService.recordItem(taskId, cleanId, "CLEAN_ARTICLE",
                            clean != null ? clean.getTitle() : "文章 " + cleanId,
                            "SUCCESS", "");
                    int ok = success.incrementAndGet();
                    lastMessage.set("已检测文章 " + cleanId + "；模式：" + modeLabel(mode, cleanIds.size()));
                    processingTaskService.updateProgress(taskId, ok, failed.get(), lastMessage.get());
                } catch (Exception e) {
                    int fail = failed.incrementAndGet();
                    lastMessage.set("文章 " + cleanId + " 虚假检测失败: " + e.getMessage() + "；模式：" + modeLabel(mode, cleanIds.size()));
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
                (failed.get() > 0 ? "虚假检测完成，部分文章失败" : "虚假检测完成")
                        + "；模式：" + modeLabel(mode, cleanIds.size()));
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
