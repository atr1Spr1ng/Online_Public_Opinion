package com.bupt.publicopinion.collection.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bupt.publicopinion.collection.client.PythonCrawlerClient;
import com.bupt.publicopinion.collection.dto.NewsDiscoverRequest;
import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.entity.CrawlTask;
import com.bupt.publicopinion.collection.entity.CrawlTaskItem;
import com.bupt.publicopinion.collection.entity.NewsSource;
import com.bupt.publicopinion.collection.mapper.ArticleRawMapper;
import com.bupt.publicopinion.collection.mapper.CrawlTaskItemMapper;
import com.bupt.publicopinion.collection.mapper.CrawlTaskMapper;
import com.bupt.publicopinion.collection.mapper.NewsSourceMapper;
import com.bupt.publicopinion.collection.service.CrawlerService;
import com.bupt.publicopinion.collection.vo.CrawlerHealthResult;
import com.bupt.publicopinion.collection.vo.CrawlerTaskSaveResult;
import com.bupt.publicopinion.collection.vo.FailedNewsCrawl;
import com.bupt.publicopinion.collection.vo.NewsCollectResult;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;
import com.bupt.publicopinion.collection.vo.NewsDiscoverResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        NewsCollectResult collectResult = pythonCrawlerClient.collectNews(request);
        NewsSource source = saveOrUpdateSource(collectResult);
        CrawlTask task = saveTask(request, collectResult, source.getId());

        for (NewsCrawlResult article : collectResult.articles()) {
            ArticleRaw articleRaw = saveOrUpdateArticle(article, collectResult);
            saveSuccessTaskItem(task.getId(), articleRaw);
        }

        for (FailedNewsCrawl failure : collectResult.failures()) {
            saveFailedTaskItem(task.getId(), failure);
        }

        return new CrawlerTaskSaveResult(
                task.getId(),
                source.getId(),
                collectResult.sourceName(),
                collectResult.sourceType(),
                collectResult.sourceUrl(),
                collectResult.totalDiscovered(),
                collectResult.totalSuccess(),
                collectResult.totalFailed(),
                task.getStatus()
        );
    }

    private NewsSource saveOrUpdateSource(NewsCollectResult collectResult) {
        NewsSource existing = newsSourceMapper.selectOne(
                new LambdaQueryWrapper<NewsSource>()
                        .eq(NewsSource::getSourceUrl, collectResult.sourceUrl())
                        .last("LIMIT 1")
        );

        if (existing != null) {
            existing.setSourceName(collectResult.sourceName());
            existing.setSourceType(collectResult.sourceType());
            existing.setStatus(1);
            newsSourceMapper.updateById(existing);
            return existing;
        }

        NewsSource source = new NewsSource();
        source.setSourceName(collectResult.sourceName());
        source.setSourceType(collectResult.sourceType());
        source.setSourceUrl(collectResult.sourceUrl());
        source.setStatus(1);
        newsSourceMapper.insert(source);
        return source;
    }

    private CrawlTask saveTask(NewsDiscoverRequest request, NewsCollectResult collectResult, Long sourceId) {
        CrawlTask task = new CrawlTask();
        task.setSourceId(sourceId);
        task.setSourceUrl(collectResult.sourceUrl());
        task.setFinalUrl(collectResult.finalUrl());
        task.setSourceName(collectResult.sourceName());
        task.setSourceType(collectResult.sourceType());
        task.setRequestLimit(request.limit());
        task.setTotalDiscovered(collectResult.totalDiscovered());
        task.setTotalSuccess(collectResult.totalSuccess());
        task.setTotalFailed(collectResult.totalFailed());
        task.setStatus(resolveTaskStatus(collectResult));
        crawlTaskMapper.insert(task);
        return task;
    }

    private ArticleRaw saveOrUpdateArticle(NewsCrawlResult article, NewsCollectResult collectResult) {
        ArticleRaw existing = articleRawMapper.selectOne(
                new LambdaQueryWrapper<ArticleRaw>()
                        .eq(ArticleRaw::getOriginalUrl, article.originalUrl())
                        .last("LIMIT 1")
        );

        ArticleRaw articleRaw = existing == null ? new ArticleRaw() : existing;
        articleRaw.setSourceName(collectResult.sourceName());
        articleRaw.setSourceType(collectResult.sourceType());
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

        if (existing == null) {
            articleRawMapper.insert(articleRaw);
        } else {
            articleRawMapper.updateById(articleRaw);
        }
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

    private void saveFailedTaskItem(Long taskId, FailedNewsCrawl failure) {
        CrawlTaskItem item = new CrawlTaskItem();
        item.setTaskId(taskId);
        item.setTitle(failure.title());
        item.setUrl(failure.url());
        item.setStatus("FAILED");
        item.setFailureReason(failure.reason());
        crawlTaskItemMapper.insert(item);
    }

    private String resolveTaskStatus(NewsCollectResult collectResult) {
        if (collectResult.totalSuccess() == 0) {
            return "FAILED";
        }
        if (collectResult.totalFailed() > 0) {
            return "PARTIAL_FAILED";
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
}
