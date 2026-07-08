package com.bupt.publicopinion.collection.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.collection.client.PythonCrawlerClient;
import com.bupt.publicopinion.collection.dto.NewsDiscoverRequest;
import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.dto.NewsSourceRequest;
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
import com.bupt.publicopinion.collection.vo.CrawlTaskDetailResult;
import com.bupt.publicopinion.collection.vo.CrawlerHealthResult;
import com.bupt.publicopinion.collection.vo.CrawlerTaskSaveResult;
import com.bupt.publicopinion.collection.vo.DiscoveredNewsLink;
import com.bupt.publicopinion.collection.vo.FailedNewsCrawl;
import com.bupt.publicopinion.collection.vo.NewsCollectResult;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;
import com.bupt.publicopinion.collection.vo.NewsDiscoverResult;
import com.bupt.publicopinion.common.vo.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CrawlerServiceImpl implements CrawlerService {

    private final PythonCrawlerClient pythonCrawlerClient;
    private final NewsSourceMapper newsSourceMapper;
    private final CrawlTaskMapper crawlTaskMapper;
    private final CrawlTaskItemMapper crawlTaskItemMapper;
    private final ArticleRawMapper articleRawMapper;
    private final ObjectMapper objectMapper;

    public CrawlerServiceImpl(
            PythonCrawlerClient pythonCrawlerClient,
            NewsSourceMapper newsSourceMapper,
            CrawlTaskMapper crawlTaskMapper,
            CrawlTaskItemMapper crawlTaskItemMapper,
            ArticleRawMapper articleRawMapper,
            ObjectMapper objectMapper
    ) {
        this.pythonCrawlerClient = pythonCrawlerClient;
        this.newsSourceMapper = newsSourceMapper;
        this.crawlTaskMapper = crawlTaskMapper;
        this.crawlTaskItemMapper = crawlTaskItemMapper;
        this.articleRawMapper = articleRawMapper;
        this.objectMapper = objectMapper;
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
    public NewsDiscoverResult discoverNewsLinks(NewsDiscoverRequest request) {
        return pythonCrawlerClient.discoverNewsLinks(request);
    }

    @Override
    public NewsCollectResult collectNews(NewsDiscoverRequest request) {
        return pythonCrawlerClient.collectNews(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrawlerTaskSaveResult createCrawlTask(NewsDiscoverRequest request) {
        NewsDiscoverResult discoverResult = pythonCrawlerClient.discoverNewsLinks(request);
        NewsSource source = saveOrUpdateSource(
                discoverResult.sourceUrl(),
                discoverResult.sourceName(),
                discoverResult.sourceType()
        );
        CrawlTask task = createPendingTask(request, discoverResult, source.getId());
        int successCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        for (DiscoveredNewsLink link : discoverResult.links()) {
            ArticleRaw existing = findArticleByOriginalUrl(link.url());
            if (existing != null) {
                duplicateCount++;
                saveDuplicateTaskItem(task.getId(), existing);
                continue;
            }

            try {
                NewsCrawlResult article = pythonCrawlerClient.crawlNews(new NewsCrawlRequest(link.url()));
                if (!"SUCCESS".equals(article.extractStatus())) {
                    failedCount++;
                    saveFailedTaskItem(task.getId(), new FailedNewsCrawl(
                            link.url(),
                            link.title(),
                            article.message()
                    ));
                    continue;
                }

                ArticleRaw articleRaw = saveArticle(article, discoverResult.sourceName(), discoverResult.sourceType());
                successCount++;
                saveSuccessTaskItem(task.getId(), articleRaw);
            } catch (RuntimeException exception) {
                failedCount++;
                saveFailedTaskItem(task.getId(), new FailedNewsCrawl(
                        link.url(),
                        link.title(),
                        exception.getMessage()
                ));
            }
        }

        task.setTotalSuccess(successCount);
        task.setTotalDuplicate(duplicateCount);
        task.setTotalFailed(failedCount);
        task.setStatus(resolveTaskStatus(successCount, duplicateCount, failedCount));
        crawlTaskMapper.updateById(task);

        return new CrawlerTaskSaveResult(
                task.getId(),
                source.getId(),
                discoverResult.sourceName(),
                discoverResult.sourceType(),
                discoverResult.sourceUrl(),
                discoverResult.totalFound(),
                successCount,
                duplicateCount,
                failedCount,
                task.getStatus()
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
        return createCrawlTask(new NewsDiscoverRequest(source.getSourceUrl(), normalizeLimit(limit)));
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
                tasks.add(createCrawlTask(new NewsDiscoverRequest(source.getSourceUrl(), taskLimit)));
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

    @Override
    public PageResult<CrawlTask> listCrawlTasks(long pageNum, long pageSize) {
        Page<CrawlTask> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        Page<CrawlTask> result = crawlTaskMapper.selectPage(
                page,
                new LambdaQueryWrapper<CrawlTask>()
                        .orderByDesc(CrawlTask::getCreateTime)
                        .orderByDesc(CrawlTask::getId)
        );
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
    public PageResult<ArticleRaw> listArticles(long pageNum, long pageSize) {
        Page<ArticleRaw> page = new Page<>(normalizePageNum(pageNum), normalizePageSize(pageSize));
        Page<ArticleRaw> result = articleRawMapper.selectPage(
                page,
                new LambdaQueryWrapper<ArticleRaw>()
                        .orderByDesc(ArticleRaw::getCreateTime)
                        .orderByDesc(ArticleRaw::getId)
        );
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
        NewsSource existing = newsSourceMapper.selectOne(
                new LambdaQueryWrapper<NewsSource>()
                        .eq(NewsSource::getSourceUrl, request.sourceUrl())
                        .last("LIMIT 1")
        );

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
        source.setSourceUrl(request.sourceUrl());
        source.setStatus(request.status());
        newsSourceMapper.insert(source);
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
        NewsSource existing = newsSourceMapper.selectOne(
                new LambdaQueryWrapper<NewsSource>()
                        .eq(NewsSource::getSourceUrl, sourceUrl)
                        .last("LIMIT 1")
        );

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
        source.setSourceUrl(sourceUrl);
        source.setStatus(1);
        newsSourceMapper.insert(source);
        return source;
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

    private ArticleRaw saveArticle(NewsCrawlResult article, String sourceName, String sourceType) {
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

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 5;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, 100);
    }
}
