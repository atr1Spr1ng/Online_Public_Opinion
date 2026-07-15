package com.bupt.publicopinion.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.analysis.entity.ArticleSentiment;
import com.bupt.publicopinion.analysis.mapper.ArticleSentimentMapper;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.mapper.ArticleRawMapper;
import com.bupt.publicopinion.content.client.PythonContentClient;
import com.bupt.publicopinion.content.dto.CleanRequest;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.exception.ContentServiceException;
import com.bupt.publicopinion.content.mapper.ArticleCleanMapper;
import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.content.service.ContentService;
import com.bupt.publicopinion.content.vo.CleanResult;
import com.bupt.publicopinion.fake.entity.ArticleFakeDetection;
import com.bupt.publicopinion.fake.mapper.ArticleFakeDetectionMapper;
import com.bupt.publicopinion.search.service.SearchSyncService;
import com.bupt.publicopinion.task.entity.ProcessingTask;
import com.bupt.publicopinion.task.service.ProcessingTaskService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ContentServiceImpl implements ContentService {

    private final ArticleCleanMapper articleCleanMapper;
    private final ArticleRawMapper articleRawMapper;
    private final ArticleSentimentMapper articleSentimentMapper;
    private final ArticleFakeDetectionMapper articleFakeDetectionMapper;
    private final PythonContentClient pythonContentClient;
    private final SearchSyncService searchSyncService;
    private final ProcessingTaskService processingTaskService;

    public ContentServiceImpl(
            ArticleCleanMapper articleCleanMapper,
            ArticleRawMapper articleRawMapper,
            ArticleSentimentMapper articleSentimentMapper,
            ArticleFakeDetectionMapper articleFakeDetectionMapper,
            PythonContentClient pythonContentClient,
            SearchSyncService searchSyncService,
            ProcessingTaskService processingTaskService
    ) {
        this.articleCleanMapper = articleCleanMapper;
        this.articleRawMapper = articleRawMapper;
        this.articleSentimentMapper = articleSentimentMapper;
        this.articleFakeDetectionMapper = articleFakeDetectionMapper;
        this.pythonContentClient = pythonContentClient;
        this.searchSyncService = searchSyncService;
        this.processingTaskService = processingTaskService;
    }

    @Override
    public CleanResult cleanArticle(CleanRequest request) {
        return cleanArticleInternal(request, null);
    }

    private CleanResult cleanArticleInternal(CleanRequest request, String mode) {
        ArticleRaw raw = articleRawMapper.selectById(request.rawId());
        if (raw == null) {
            throw new ContentServiceException("原始文章不存在: " + request.rawId());
        }

        CleanResult result = pythonContentClient.clean(raw, mode);
        saveCleanResult(raw, result);
        return result;
    }

    @Override
    public ProcessingTask batchClean(List<Long> rawIds) {
        List<Long> ids = normalizeIds(rawIds);
        ProcessingTask task = processingTaskService.createTask("CLEAN", "RAW_ARTICLE", ids.size());
        final UserContext.UserContextInfo ctx = UserContext.get();
        processingTaskService.runAsync(task.getId(), ctx, taskId -> processBatchClean(taskId, ids));
        return task;
    }

    private void processBatchClean(Long taskId, List<Long> rawIds) {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicReference<String> lastMessage = new AtomicReference<>("");
        final UserContext.UserContextInfo ctx = UserContext.get();
        Semaphore sem = new Semaphore(5);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Long rawId : rawIds) {
            futures.add(CompletableFuture.runAsync(() -> {
                UserContext.set(ctx);
                boolean acquired = false;
                try {
                    sem.acquire();
                    acquired = true;
                    ArticleRaw raw = articleRawMapper.selectById(rawId);
                    cleanArticleInternal(new CleanRequest(rawId), "fast");
                    processingTaskService.recordItem(taskId, rawId, "RAW_ARTICLE",
                            raw != null ? raw.getTitle() : "原始文章 " + rawId,
                            "SUCCESS", "");
                    int ok = success.incrementAndGet();
                    lastMessage.set("已清洗原始文章 " + rawId);
                    processingTaskService.updateProgress(taskId, ok, failed.get(), lastMessage.get());
                } catch (Exception e) {
                    int fail = failed.incrementAndGet();
                    lastMessage.set("原始文章 " + rawId + " 清洗失败: " + e.getMessage());
                    processingTaskService.recordItem(taskId, rawId, "RAW_ARTICLE",
                            resolveRawTitle(rawId), "FAILED", e.getMessage());
                    processingTaskService.updateProgress(taskId, success.get(), fail, lastMessage.get());
                } finally {
                    if (acquired) sem.release();
                    UserContext.clear();
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        processingTaskService.finish(taskId, success.get(), failed.get(),
                failed.get() > 0 ? "清洗完成，部分文章失败" : "清洗完成");
    }

    @Override
    public ArticleClean getCleanedArticle(Long id) {
        ArticleClean article = articleCleanMapper.selectById(id);
        if (article == null) {
            throw new ContentServiceException("清洗后的文章不存在: " + id);
        }
        return article;
    }

    @Override
    public PageResult<ArticleClean> listCleanedArticles(long pageNum, long pageSize, boolean excludeAnalyzed, boolean excludeDetected) {
        LambdaQueryWrapper<ArticleClean> wrapper = new LambdaQueryWrapper<ArticleClean>()
                .orderByDesc(ArticleClean::getCreateTime);

        if (excludeAnalyzed) {
            wrapper.notInSql(ArticleClean::getId, "SELECT clean_id FROM article_sentiment");
        }
        if (excludeDetected) {
            wrapper.notInSql(ArticleClean::getId, "SELECT clean_id FROM article_fake_detection");
        }
        if (!"ADMIN".equals(UserContext.getRequired().role())) {
            wrapper.eq(ArticleClean::getUserId, UserContext.getRequired().userId());
        }

        Page<ArticleClean> page = new Page<>(pageNum, pageSize);
        Page<ArticleClean> result = articleCleanMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    private void saveCleanResult(ArticleRaw raw, CleanResult result) {
        ArticleClean clean = new ArticleClean();
        clean.setRawId(raw.getId());
        clean.setTitle(result.title());
        clean.setContent(result.content());
        clean.setKeywords(result.keywords());
        clean.setSummary(result.summary());
        clean.setLanguage(result.language());
        clean.setPublishedAt(raw.getPublishedAt());
        clean.setSourceName(raw.getSourceName());
        clean.setStatus(result.status());

        // ES more_like_this 内容去重
        boolean isDuplicate = false;
        try {
            var similar = searchSyncService.findSimilar(result.title(), result.content(), 1);
            isDuplicate = !similar.isEmpty();
        } catch (Exception e) {
            // ES 不可用时不阻断清洗流程
        }

        clean.setSimhash(isDuplicate ? -1L : 0L);
        clean.setUserId(UserContext.getRequired().userId());
        articleCleanMapper.insert(clean);
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return new ArrayList<>(new LinkedHashSet<>(ids.stream()
                .filter(id -> id != null && id > 0)
                .toList()));
    }

    private String resolveRawTitle(Long rawId) {
        ArticleRaw raw = articleRawMapper.selectById(rawId);
        if (raw == null) return "原始文章 " + rawId;
        return raw.getTitle() != null && !raw.getTitle().isBlank() ? raw.getTitle() : "原始文章 " + rawId;
    }

    @Override
    public void deleteCleanedArticle(Long id) {
        ArticleClean article = articleCleanMapper.selectById(id);
        if (article == null) {
            throw new RuntimeException("清洗文章不存在: " + id);
        }
        // 级联删除情感分析和虚假检测结果
        articleSentimentMapper.delete(
                new LambdaQueryWrapper<ArticleSentiment>().eq(ArticleSentiment::getCleanId, id));
        articleFakeDetectionMapper.delete(
                new LambdaQueryWrapper<ArticleFakeDetection>().eq(ArticleFakeDetection::getCleanId, id));
        articleCleanMapper.deleteById(id);
        searchSyncService.deleteArticle(id);
    }
}
