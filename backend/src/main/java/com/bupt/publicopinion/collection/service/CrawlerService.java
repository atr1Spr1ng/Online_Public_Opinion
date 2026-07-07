package com.bupt.publicopinion.collection.service;

import com.bupt.publicopinion.collection.dto.NewsDiscoverRequest;
import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.vo.CrawlerHealthResult;
import com.bupt.publicopinion.collection.vo.NewsCollectResult;
import com.bupt.publicopinion.collection.vo.NewsDiscoverResult;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;

public interface CrawlerService {

    CrawlerHealthResult checkHealth();

    NewsCrawlResult crawlNews(NewsCrawlRequest request);

    NewsDiscoverResult discoverNewsLinks(NewsDiscoverRequest request);

    NewsCollectResult collectNews(NewsDiscoverRequest request);
}
