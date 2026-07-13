package com.bupt.publicopinion.collection.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.collection.client.PythonCrawlerClient;
import com.bupt.publicopinion.collection.dto.NewsDiscoverRequest;
import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.dto.NewsSourceRequest;
import com.bupt.publicopinion.collection.dto.PythonTopicSearchRequest;
import com.bupt.publicopinion.collection.dto.TopicSearchRequest;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.entity.CrawlTask;
import com.bupt.publicopinion.collection.entity.CrawlTaskItem;
import com.bupt.publicopinion.collection.entity.NewsSource;
import com.bupt.publicopinion.collection.mapper.ArticleRawMapper;
import com.bupt.publicopinion.collection.mapper.CrawlTaskItemMapper;
import com.bupt.publicopinion.collection.mapper.CrawlTaskMapper;
import com.bupt.publicopinion.collection.mapper.NewsSourceMapper;
import com.bupt.publicopinion.collection.service.CrawlerService;
import com.bupt.publicopinion.collection.vo.BatchCrawlerTaskFailure;
import com.bupt.publicopinion.collection.vo.BatchCrawlerTaskResult;
import com.bupt.publicopinion.collection.vo.BatchTopicResult;
import com.bupt.publicopinion.collection.vo.CrawlTaskDetailResult;
import com.bupt.publicopinion.collection.vo.CrawlerHealthResult;
import com.bupt.publicopinion.collection.vo.CrawlerTaskSaveResult;
import com.bupt.publicopinion.collection.vo.DiscoveredNewsLink;
import com.bupt.publicopinion.collection.vo.FailedNewsCrawl;
import com.bupt.publicopinion.collection.vo.NewsCollectResult;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;
import com.bupt.publicopinion.collection.vo.NewsDiscoverResult;
import com.bupt.publicopinion.collection.vo.TopicSearchResult;
import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.vo.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class CrawlerServiceImpl implements CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerServiceImpl.class);

    private final PythonCrawlerClient pythonCrawlerClient;
    private final NewsSourceMapper newsSourceMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final CrawlTaskItemMapper crawlTaskItemMapper;
    private final ArticleRawMapper articleRawMapper;
    private final ObjectMapper objectMapper;
    private final Executor crawlTaskExecutor;

    public CrawlerServiceImpl(
            PythonCrawlerClient pythonCrawlerClient,
            NewsSourceMapper newsSourceMapper,
            CrawlTaskMapper crawlTaskMapper,
            CrawlTaskItemMapper crawlTaskItemMapper,
            ArticleRawMapper articleRawMapper,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Qualifier("crawlTaskExecutor") Executor crawlTaskExecutor
    ) {
        this.pythonCrawlerClient = pythonCrawlerClient;
        this.newsSourceMapper = newsSourceMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.crawlTaskItemMapper = crawlTaskItemMapper;
        this.articleRawMapper = articleRawMapper;
        this.objectMapper = objectMapper;
        this.crawlTaskExecutor = crawlTaskExecutor;
    }

    @Override
    public CrawlerHealthResult checkHealth() {
        return pythonCrawlerClient.checkHealth();
    }

    @Override
    public NewsCrawlResult crawlNews(NewsCrawlRequest request) {
        return pythonCrawlerClient.crawlNews(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleRaw crawlAndSaveArticle(NewsCrawlRequest request) {
        ArticleRaw existing = findArticleByOriginalUrl(request.url());
        if (existing != null) {
            throw new IllegalArgumentException("该文章已存在，请勿重复采集");
        }

        NewsCrawlResult article = pythonCrawlerClient.crawlNews(request);
        if (!"SUCCESS".equals(article.extractStatus())) {
            throw new RuntimeException("文章采集失败: " + article.message());
        }

        // 从URL提取来源名称
        String sourceName = extractDomainName(request.url());
        String sourceType = "manual";

        return saveArticle(article, sourceName, sourceType, UserContext.get().userId());
    }

    private String extractDomainName(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            if (host == null) return "未知来源";
            return host.replace("www.", "").replace("mini.", "");
        } catch (Exception e) {
            return "未知来源";
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchTopicResult searchAndCollectByTopic(TopicSearchRequest request) {
        List<NewsSource> sources = newsSourceMapper.selectBatchIds(request.getSourceIds());
        BatchTopicResult result = BatchTopicResult.empty(request.getKeyword(), sources.size());

        for (NewsSource source : sources) {
            try {
                TopicSearchResult searchResult = pythonCrawlerClient.searchByTopic(
                        new PythonTopicSearchRequest(request.getKeyword(), source.getSourceUrl(), request.getLimit())
                );
                result.addResult(searchResult.sourceName(), searchResult.totalFound(),
                        searchResult.totalSuccess(), searchResult.totalFailed());

                if (searchResult.articles() != null) {
                    for (NewsCrawlResult article : searchResult.articles()) {
                        ArticleRaw existing = findArticleByOriginalUrl(article.originalUrl());
                        if (existing != null) continue;
                        if (!"SUCCESS".equals(article.extractStatus())) continue;
                        ArticleRaw saved = saveArticle(article, searchResult.sourceName(), searchResult.sourceType(), UserContext.get().userId());
                        result.addArticle(saved);
                    }
                }
            } catch (Exception e) {
                log.warn("搜索新闻源 {} ({}) 失败: {}", source.getSourceName(), source.getSourceUrl(), e.getMessage());
                result.addResult(source.getSourceName(), 0, 0, 1);
            }
        }
        return result;
    }

    @Override
    public NewsDiscoverResult discoverNewsLinks(NewsDiscoverRequest request) {
        return pythonCrawlerClient.discoverNewsLinks(request);
    }

    @Override
    public NewsCollectResult collectNews(NewsDiscoverRequest request) {
        return pythonCrawlerClient.collectNews(request);
    }

    @Override
    public CrawlerTaskSaveResult createCrawlTask(NewsDiscoverRequest request) {
        CrawlTask task = new CrawlTask();
        task.setSourceUrl(request.url());
        task.setRequestLimit(request.limit());
        task.setTotalSuccess(0);
        task.setTotalDuplicate(0);
        task.setTotalFailed(0);
        task.setStatus("RUNNING");
        task.setUserId(UserContext.get().userId());
        task.setCreateTime(LocalDateTime.now());
        crawlTaskMapper.insert(task);

        final Long userId = task.getUserId();
        CompletableFuture.runAsync(() -> doCrawl(task.getId(), request, userId), crawlTaskExecutor)
                .exceptionally(ex -> {
                    log.error("异步采集任务 {} 异常: {}", task.getId(), ex.getMessage());
                    return null;
                });

        return new CrawlerTaskSaveResult(
                task.getId(), null, null, null, request.url(),
                0, 0, 0, 0, "RUNNING"
        );
    }

    @Override
    public CrawlerTaskSaveResult createCrawlTaskBySource(Long sourceId, Integer limit) {
        NewsSource source = newsSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("新闻源不存在: " + sourceId);
        }
        if (source.getStatus() == null || source.getStatus() != 1) {
            throw new IllegalArgumentException("新闻源未启用: " + sourceId);
        }
        int taskLimit = normalizeLimit(limit);

        CrawlTask task = new CrawlTask();
        task.setSourceId(sourceId);
        task.setSourceUrl(source.getSourceUrl());
        task.setSourceName(source.getSourceName());
        task.setSourceType(source.getSourceType());
        task.setRequestLimit(taskLimit);
        task.setTotalSuccess(0);
        task.setTotalDuplicate(0);
        task.setTotalFailed(0);
        task.setStatus("RUNNING");
        task.setUserId(UserContext.get().userId());
        task.setCreateTime(LocalDateTime.now());
        crawlTaskMapper.insert(task);

        final Long userId = task.getUserId();
        CompletableFuture.runAsync(
                () -> doCrawl(task.getId(), new NewsDiscoverRequest(source.getSourceUrl(), taskLimit), userId),
                crawlTaskExecutor
        ).exceptionally(ex -> {
            log.error("异步采集任务 {} 异常: {}", task.getId(), ex.getMessage());
            return null;
        });

        return new CrawlerTaskSaveResult(
                task.getId(), sourceId, source.getSourceName(), source.getSourceType(),
                source.getSourceUrl(), 0, 0, 0, 0, "RUNNING"
        );
    }

    @Override
    public BatchCrawlerTaskResult createCrawlTasksForAllEnabledSources(Integer limit) {
        List<NewsSource> sources = newsSourceMapper.selectList(
                new LambdaQueryWrapper<NewsSource>()
                        .eq(NewsSource::getStatus, 1)
                        .orderByAsc(NewsSource::getId)
        );
        List<CrawlerTaskSaveResult> tasks = new ArrayList<>();
        List<BatchCrawlerTaskFailure> failures = new ArrayList<>();
        int taskLimit = normalizeLimit(limit);

        for (NewsSource source : sources) {
            try {
                CrawlTask task = new CrawlTask();
                task.setSourceId(source.getId());
                task.setSourceUrl(source.getSourceUrl());
                task.setSourceName(source.getSourceName());
                task.setSourceType(source.getSourceType());
                task.setRequestLimit(taskLimit);
                task.setTotalSuccess(0);
                task.setTotalDuplicate(0);
                task.setTotalFailed(0);
                task.setStatus("RUNNING");
                task.setUserId(UserContext.get().userId());
                task.setCreateTime(LocalDateTime.now());
                crawlTaskMapper.insert(task);

                final Long userId = UserContext.get().userId();
                CompletableFuture.runAsync(
                        () -> doCrawl(task.getId(), new NewsDiscoverRequest(source.getSourceUrl(), taskLimit), userId),
                        crawlTaskExecutor
                ).exceptionally(ex -> {
                    log.error("异步采集任务 {} 异常: {}", task.getId(), ex.getMessage());
                    return null;
                });

                tasks.add(new CrawlerTaskSaveResult(
                        task.getId(), source.getId(), source.getSourceName(), source.getSourceType(),
                        source.getSourceUrl(), 0, 0, 0, 0, "RUNNING"
                ));
            } catch (RuntimeException exception) {
                failures.add(new BatchCrawlerTaskFailure(
                        source.getId(),
                        source.getSourceName(),
                        source.getSourceUrl(),
                        exception.getMessage()
                ));
            }
        }

        return new BatchCrawlerTaskResult(
                sources.size(),
                tasks.size(),
                failures.size(),
                tasks,
                failures
        );
    }

    private void doCrawl(Long taskId, NewsDiscoverRequest request, Long userId) {
        try {
            NewsDiscoverResult discoverResult = pythonCrawlerClient.discoverNewsLinks(request);
            NewsSource source = saveOrUpdateSource(
                    discoverResult.sourceUrl(),
                    discoverResult.sourceName(),
                    discoverResult.sourceType()
            );

            CrawlTask task = crawlTaskMapper.selectById(taskId);
            if (task == null) return;
            task.setSourceId(source.getId());
            task.setFinalUrl(discoverResult.finalUrl());
            task.setSourceName(discoverResult.sourceName());
            task.setSourceType(discoverResult.sourceType());
            task.setTotalDiscovered(discoverResult.totalFound());
            crawlTaskMapper.updateById(task);

            int successCount = 0;
            int duplicateCount = 0;
            int failedCount = 0;

            for (DiscoveredNewsLink link : discoverResult.links()) {
                ArticleRaw existing = findArticleByOriginalUrl(link.url());
                if (existing != null) {
                    duplicateCount++;
                    saveDuplicateTaskItem(taskId, existing);
                    continue;
                }

                try {
                    NewsCrawlResult article = pythonCrawlerClient.crawlNews(new NewsCrawlRequest(link.url()));
                    if (!"SUCCESS".equals(article.extractStatus())) {
                        failedCount++;
                        saveFailedTaskItem(taskId, new FailedNewsCrawl(
                                link.url(), link.title(), article.message()
                        ));
                        continue;
                    }

                    ArticleRaw articleRaw = saveArticle(article, discoverResult.sourceName(), discoverResult.sourceType(), userId);
                    successCount++;
                    saveSuccessTaskItem(taskId, articleRaw);
                } catch (RuntimeException exception) {
                    failedCount++;
                    saveFailedTaskItem(taskId, new FailedNewsCrawl(
                            link.url(), link.title(), exception.getMessage()
                    ));
                }
            }

            task.setTotalSuccess(successCount);
            task.setTotalDuplicate(duplicateCount);
            task.setTotalFailed(failedCount);
            task.setStatus(resolveTaskStatus(successCount, duplicateCount, failedCount));
            crawlTaskMapper.updateById(task);
        } catch (Exception e) {
            log.error("采集任务 {} 执行失败: {}", taskId, e.getMessage());
            CrawlTask task = crawlTaskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("FAILED");
                task.setTotalFailed(task.getTotalDiscovered() != null ? task.getTotalDiscovered() : 0);
                crawlTaskMapper.updateById(task);
            }
        }
    }

    @Override
    public PageResult<CrawlTask> listCrawlTasks(long pageNum, long pageSize) {
        LambdaQueryWrapper<CrawlTask> taskWrapper = new LambdaQueryWrapper<CrawlTask>()
                .orderByDesc(CrawlTask::getCreateTime)
                .orderByDesc(CrawlTask::getId);
        if (!isAdmin()) {
            taskWrapper.eq(CrawlTask::getUserId, currentUserId());
        }

        Page<CrawlTask> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        Page<CrawlTask> result = crawlTaskMapper.selectPage(page, taskWrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public CrawlTaskDetailResult getCrawlTaskDetail(Long taskId) {
        CrawlTask task = crawlTaskMapper.selectById(taskId);
        List<CrawlTaskItem> items = crawlTaskItemMapper.selectList(
                new LambdaQueryWrapper<CrawlTaskItem>()
                        .eq(CrawlTaskItem::getTaskId, taskId)
                        .orderByAsc(CrawlTaskItem::getId)
        );
        return new CrawlTaskDetailResult(task, items);
    }

    @Override
    public PageResult<ArticleRaw> listArticles(long pageNum, long pageSize, boolean excludeCleaned) {
        LambdaQueryWrapper<ArticleRaw> wrapper = new LambdaQueryWrapper<ArticleRaw>()
                .orderByDesc(ArticleRaw::getCreateTime)
                .orderByDesc(ArticleRaw::getId);

        if (excludeCleaned) {
            wrapper.notInSql(ArticleRaw::getId, "SELECT raw_id FROM article_clean");
        }

        if (!isAdmin()) {
            wrapper.eq(ArticleRaw::getUserId, currentUserId());
        }

        Page<ArticleRaw> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        Page<ArticleRaw> result = articleRawMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PageResult<NewsSource> listNewsSources(long pageNum, long pageSize) {
        Page<NewsSource> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        Page<NewsSource> result = newsSourceMapper.selectPage(
                page,
                new LambdaQueryWrapper<NewsSource>()
                        .orderByDesc(NewsSource::getCreateTime)
                        .orderByDesc(NewsSource::getId)
        );
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NewsSource createNewsSource(NewsSourceRequest request) {
        String normalizedUrl = normalizeUrl(request.sourceUrl());
        NewsSource existing = newsSourceMapper.selectOne(buildUrlLookupQuery(normalizedUrl));

        if (existing != null) {
            existing.setSourceName(request.sourceName());
            existing.setSourceType(request.sourceType());
            existing.setStatus(request.status());
            newsSourceMapper.updateById(existing);
            return existing;
        }

        NewsSource source = new NewsSource();
        source.setSourceName(request.sourceName());
        source.setSourceType(request.sourceType());
        source.setSourceUrl(normalizedUrl);
        source.setStatus(request.status());
        newsSourceMapper.insert(source);
        return source;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NewsSource updateNewsSource(Long sourceId, NewsSourceRequest request) {
        NewsSource source = newsSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("新闻源不存在: " + sourceId);
        }
        source.setSourceName(request.sourceName());
        source.setSourceType(request.sourceType());
        source.setSourceUrl(normalizeUrl(request.sourceUrl()));
        if (request.status() != null) {
            source.setStatus(request.status());
        }
        newsSourceMapper.updateById(source);
        return source;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NewsSource updateNewsSourceStatus(Long sourceId, Integer status) {
        NewsSource source = newsSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("新闻源不存在: " + sourceId);
        }
        source.setStatus(status != null && status == 1 ? 1 : 0);
        newsSourceMapper.updateById(source);
        return source;
    }

    private NewsSource saveOrUpdateSource(String sourceUrl, String sourceName, String sourceType) {
        String normalizedUrl = normalizeUrl(sourceUrl);
        NewsSource existing = newsSourceMapper.selectOne(buildUrlLookupQuery(normalizedUrl));

        if (existing != null) {
            existing.setSourceName(sourceName);
            existing.setSourceType(sourceType);
            existing.setStatus(1);
            newsSourceMapper.updateById(existing);
            return existing;
        }

        NewsSource source = new NewsSource();
        source.setSourceName(sourceName);
        source.setSourceType(sourceType);
        source.setSourceUrl(normalizedUrl);
        source.setStatus(1);
        newsSourceMapper.insert(source);
        return source;
    }

    /**
     * Build a query that matches a URL regardless of trailing-slash or www-prefix variance.
     */
    private LambdaQueryWrapper<NewsSource> buildUrlLookupQuery(String normalizedUrl) {
        LambdaQueryWrapper<NewsSource> wrapper = new LambdaQueryWrapper<NewsSource>()
                .eq(NewsSource::getSourceUrl, normalizedUrl)
                .or()
                .eq(NewsSource::getSourceUrl, normalizedUrl + "/");

        try {
            java.net.URI uri = new java.net.URI(normalizedUrl);
            String host = uri.getHost();
            if (host != null) {
                String wwwVariant;
                if (host.startsWith("www.")) {
                    wwwVariant = host.substring(4);
                } else {
                    wwwVariant = "www." + host;
                }
                String wwwUrl = new java.net.URI(uri.getScheme(), uri.getUserInfo(), wwwVariant,
                        uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
                wrapper.or().eq(NewsSource::getSourceUrl, wwwUrl)
                       .or().eq(NewsSource::getSourceUrl, wwwUrl + "/");
            }
        } catch (java.net.URISyntaxException ignored) {
        }

        return wrapper.last("LIMIT 1");
    }

    /**
     * Normalize a URL for consistent lookup: strip trailing slash and lowercase host.
     */
    private String normalizeUrl(String url) {
        if (url == null) return null;
        String normalized = url.strip();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            java.net.URI uri = new java.net.URI(normalized);
            String host = uri.getHost();
            if (host != null) {
                String cleanHost = host.toLowerCase().replaceFirst("^www\\.", "");
                normalized = new java.net.URI(uri.getScheme(), uri.getUserInfo(), cleanHost, uri.getPort(),
                        uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
            }
        } catch (java.net.URISyntaxException e) {
            normalized = normalized.toLowerCase();
        }
        return normalized;
    }

    private CrawlTask createPendingTask(NewsDiscoverRequest request, NewsDiscoverResult discoverResult, Long sourceId) {
        CrawlTask task = new CrawlTask();
        task.setSourceId(sourceId);
        task.setSourceUrl(discoverResult.sourceUrl());
        task.setFinalUrl(discoverResult.finalUrl());
        task.setSourceName(discoverResult.sourceName());
        task.setSourceType(discoverResult.sourceType());
        task.setRequestLimit(request.limit());
        task.setTotalDiscovered(discoverResult.totalFound());
        task.setTotalSuccess(0);
        task.setTotalDuplicate(0);
        task.setTotalFailed(0);
        task.setStatus("RUNNING");
        crawlTaskMapper.insert(task);
        return task;
    }

    private ArticleRaw findArticleByOriginalUrl(String originalUrl) {
        return articleRawMapper.selectOne(
                new LambdaQueryWrapper<ArticleRaw>()
                        .eq(ArticleRaw::getOriginalUrl, originalUrl)
                        .last("LIMIT 1")
        );
    }

    private ArticleRaw saveArticle(NewsCrawlResult article, String sourceName, String sourceType, Long userId) {
        ArticleRaw articleRaw = new ArticleRaw();
        articleRaw.setSourceName(sourceName);
        articleRaw.setSourceType(sourceType);
        articleRaw.setOriginalUrl(article.originalUrl());
        articleRaw.setFinalUrl(article.finalUrl());
        articleRaw.setStatusCode(article.statusCode());
        articleRaw.setTitle(article.title());
        articleRaw.setAuthorsJson(toJson(article.authors()));
        articleRaw.setPublishedAt(article.publishedAt());
        articleRaw.setContent(article.content());
        articleRaw.setContentLength(article.contentLength());
        articleRaw.setExtractStatus(article.extractStatus());
        articleRaw.setMessage(article.message());
        articleRaw.setMainImage(article.mainImage());
        articleRaw.setLanguage(article.language());
        articleRaw.setFetchedAt(article.fetchedAt() == null ? null : article.fetchedAt().toLocalDateTime());
        articleRaw.setUserId(userId);

        articleRawMapper.insert(articleRaw);
        return articleRaw;
    }

    private void saveSuccessTaskItem(Long taskId, ArticleRaw articleRaw) {
        CrawlTaskItem item = new CrawlTaskItem();
        item.setTaskId(taskId);
        item.setArticleId(articleRaw.getId());
        item.setTitle(articleRaw.getTitle());
        item.setUrl(articleRaw.getOriginalUrl());
        item.setStatus("SUCCESS");
        crawlTaskItemMapper.insert(item);
    }

    private void saveDuplicateTaskItem(Long taskId, ArticleRaw articleRaw) {
        CrawlTaskItem item = new CrawlTaskItem();
        item.setTaskId(taskId);
        item.setArticleId(articleRaw.getId());
        item.setTitle(articleRaw.getTitle());
        item.setUrl(articleRaw.getOriginalUrl());
        item.setStatus("DUPLICATE");
        item.setFailureReason("文章已存在，跳过重复入库");
        crawlTaskItemMapper.insert(item);
    }

    private void saveFailedTaskItem(Long taskId, FailedNewsCrawl failure) {
        CrawlTaskItem item = new CrawlTaskItem();
        item.setTaskId(taskId);
        item.setTitle(failure.title());
        item.setUrl(failure.url());
        item.setStatus("FAILED");
        item.setFailureReason(failure.reason());
        crawlTaskItemMapper.insert(item);
    }

    private String resolveTaskStatus(int successCount, int duplicateCount, int failedCount) {
        if (successCount == 0 && duplicateCount > 0 && failedCount == 0) {
            return "DUPLICATE";
        }
        if (successCount == 0) {
            return "FAILED";
        }
        if (failedCount > 0) {
            return "PARTIAL_FAILED";
        }
        if (duplicateCount > 0) {
            return "SUCCESS_WITH_DUPLICATE";
        }
        return "SUCCESS";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private long normalizePageNum(long pageNum) {
        return Math.max(pageNum, 1);
    }

    private long normalizePageSize(long pageSize) {
        if (pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private boolean isAdmin() {
        return "ADMIN".equals(UserContext.get().role());
    }

    private Long currentUserId() {
        return UserContext.get().userId();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 5;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 100);
    }

    @Override
    public void deleteArticle(Long id) {
        ArticleRaw article = articleRawMapper.selectById(id);
        if (article == null) {
            throw new IllegalArgumentException("文章不存在: " + id);
        }
        // Delete related task items
        crawlTaskItemMapper.delete(
                new LambdaQueryWrapper<CrawlTaskItem>()
                        .eq(CrawlTaskItem::getArticleId, id)
        );
        // Delete the raw article
        articleRawMapper.deleteById(id);
    }

    @Override
    public void deleteCrawlTask(Long id) {
        CrawlTask task = crawlTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("采集任务不存在: " + id);
        }
        // Delete related task items
        crawlTaskItemMapper.delete(
                new LambdaQueryWrapper<CrawlTaskItem>()
                        .eq(CrawlTaskItem::getTaskId, id)
        );
        // Delete the task
        crawlTaskMapper.deleteById(id);
    }
}
