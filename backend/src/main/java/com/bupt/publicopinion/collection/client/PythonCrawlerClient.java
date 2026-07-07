package com.bupt.publicopinion.collection.client;

import com.bupt.publicopinion.collection.dto.NewsDiscoverRequest;
import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.exception.CrawlerServiceException;
import com.bupt.publicopinion.collection.vo.CrawlerHealthResult;
import com.bupt.publicopinion.collection.vo.NewsCollectResult;
import com.bupt.publicopinion.collection.vo.NewsDiscoverResult;
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

    public CrawlerHealthResult checkHealth() {
        try {
            CrawlerHealthResult result = crawlerRestClient.get()
                    .uri("/internal/health")
                    .retrieve()
                    .body(CrawlerHealthResult.class);

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

    public NewsCrawlResult crawlNews(NewsCrawlRequest request) {
        try {
            NewsCrawlResult result = crawlerRestClient.post()
                    .uri("/internal/crawler/news/crawl")
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

    public NewsDiscoverResult discoverNewsLinks(NewsDiscoverRequest request) {
        try {
            NewsDiscoverResult result = crawlerRestClient.post()
                    .uri("/internal/crawler/news/discover")
                    .body(request)
                    .retrieve()
                    .body(NewsDiscoverResult.class);

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

    public NewsCollectResult collectNews(NewsDiscoverRequest request) {
        try {
            NewsCollectResult result = crawlerRestClient.post()
                    .uri("/internal/crawler/news/collect")
                    .body(request)
                    .retrieve()
                    .body(NewsCollectResult.class);

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
