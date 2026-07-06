package com.bupt.publicopinion.collection.service.impl;

import com.bupt.publicopinion.collection.client.PythonCrawlerClient;
import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.service.CrawlerService;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;
import org.springframework.stereotype.Service;

@Service
public class CrawlerServiceImpl implements CrawlerService {

    private final PythonCrawlerClient pythonCrawlerClient;

    public CrawlerServiceImpl(PythonCrawlerClient pythonCrawlerClient) {
        this.pythonCrawlerClient = pythonCrawlerClient;
    }

    @Override
    public NewsCrawlResult crawlNews(NewsCrawlRequest request) {
        return pythonCrawlerClient.crawlNews(request);
    }
}
