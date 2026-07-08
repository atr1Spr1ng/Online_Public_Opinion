package com.bupt.publicopinion.collection.service;

import com.bupt.publicopinion.collection.dto.NewsSourceRequest;
import com.bupt.publicopinion.collection.dto.NewsDiscoverRequest;
import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.entity.CrawlTask;
import com.bupt.publicopinion.collection.entity.NewsSource;
import com.bupt.publicopinion.collection.vo.BatchCrawlerTaskResult;
import com.bupt.publicopinion.collection.vo.CrawlTaskDetailResult;
import com.bupt.publicopinion.collection.vo.CrawlerHealthResult;
import com.bupt.publicopinion.collection.vo.CrawlerTaskSaveResult;
import com.bupt.publicopinion.collection.vo.NewsCollectResult;
import com.bupt.publicopinion.collection.vo.NewsDiscoverResult;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;
import com.bupt.publicopinion.common.vo.PageResult;

public interface CrawlerService {

    CrawlerHealthResult checkHealth();

    NewsCrawlResult crawlNews(NewsCrawlRequest request);

    NewsDiscoverResult discoverNewsLinks(NewsDiscoverRequest request);

    NewsCollectResult collectNews(NewsDiscoverRequest request);

    CrawlerTaskSaveResult createCrawlTask(NewsDiscoverRequest request);

    CrawlerTaskSaveResult createCrawlTaskBySource(Long sourceId, Integer limit);

    BatchCrawlerTaskResult createCrawlTasksForAllEnabledSources(Integer limit);

    PageResult<CrawlTask> listCrawlTasks(long pageNum, long pageSize);

    CrawlTaskDetailResult getCrawlTaskDetail(Long taskId);

    PageResult<ArticleRaw> listArticles(long pageNum, long pageSize);

    PageResult<NewsSource> listNewsSources(long pageNum, long pageSize);

    NewsSource createNewsSource(NewsSourceRequest request);

    NewsSource updateNewsSourceStatus(Long sourceId, Integer status);
}
