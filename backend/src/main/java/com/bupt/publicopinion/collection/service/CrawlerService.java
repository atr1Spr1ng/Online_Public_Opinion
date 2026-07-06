package com.bupt.publicopinion.collection.service;

import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;

public interface CrawlerService {

    NewsCrawlResult crawlNews(NewsCrawlRequest request);
}
