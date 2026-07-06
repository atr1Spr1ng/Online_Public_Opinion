package com.bupt.publicopinion.collection.client;

import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.exception.CrawlerServiceException;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonCrawlerClient {

    private final RestClient crawlerRestClient;

    public PythonCrawlerClient(RestClient crawlerRestClient) {
        this.crawlerRestClient = crawlerRestClient;
    }

    public NewsCrawlResult crawlNews(NewsCrawlRequest request) {
        try {
            NewsCrawlResult result = crawlerRestClient.post()
                    .uri("/internal/crawler/news/test")
                    .body(request)
                    .retrieve()
                    .body(NewsCrawlResult.class);

            if (result == null) {
                throw new CrawlerServiceException("Python 爬虫服务返回空响应");
            }
            return result;
        } catch (CrawlerServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CrawlerServiceException("调用 Python 爬虫服务失败", exception);
        }
    }
}
