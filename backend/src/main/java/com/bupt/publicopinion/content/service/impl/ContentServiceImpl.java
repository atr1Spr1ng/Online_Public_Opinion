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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@Service
public class ContentServiceImpl implements ContentService {

    private final ArticleCleanMapper articleCleanMapper;
    private final ArticleRawMapper articleRawMapper;
    private final ArticleSentimentMapper articleSentimentMapper;
    private final ArticleFakeDetectionMapper articleFakeDetectionMapper;
    private final PythonContentClient pythonContentClient;
    private final SearchSyncService searchSyncService;

    public ContentServiceImpl(
            ArticleCleanMapper articleCleanMapper,
            ArticleRawMapper articleRawMapper,
            ArticleSentimentMapper articleSentimentMapper,
            ArticleFakeDetectionMapper articleFakeDetectionMapper,
            PythonContentClient pythonContentClient,
            SearchSyncService searchSyncService
    ) {
        this.articleCleanMapper = articleCleanMapper;
        this.articleRawMapper = articleRawMapper;
        this.articleSentimentMapper = articleSentimentMapper;
        this.articleFakeDetectionMapper = articleFakeDetectionMapper;
        this.pythonContentClient = pythonContentClient;
        this.searchSyncService = searchSyncService;
    }

    @Override
    public CleanResult cleanArticle(CleanRequest request) {
        ArticleRaw raw = articleRawMapper.selectById(request.rawId());
        if (raw == null) {
            throw new ContentServiceException("原始文章不存在: " + request.rawId());
        }

        CleanResult result = pythonContentClient.clean(raw);
        saveCleanResult(raw, result);
        return result;
    }

    @Override
    public List<CleanResult> batchClean(List<Long> rawIds) {
        List<CleanResult> results = new ArrayList<>();
        final UserContext.UserContextInfo ctx = UserContext.get();
        Semaphore sem = new Semaphore(5);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Long rawId : rawIds) {
            futures.add(CompletableFuture.runAsync(() -> {
                UserContext.set(ctx);
                try {
                    sem.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    CleanResult r = cleanArticle(new CleanRequest(rawId));
                    synchronized (results) { results.add(r); }
                } catch (Exception e) {
                    synchronized (results) { results.add(CleanResult.failed(rawId, e.getMessage())); }
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
    }
}
