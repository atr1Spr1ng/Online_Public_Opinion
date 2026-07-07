package com.bupt.publicopinion.collection.controller;

import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.service.CrawlerService;
import com.bupt.publicopinion.collection.vo.CrawlerHealthResult;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crawler")
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @PostMapping("/test")
    public String test() {
        return "crawler test success";
    }

    @GetMapping("/health")
    public CrawlerHealthResult health() {
        return crawlerService.checkHealth();
    }

    @PostMapping("/news/crawl")
    public NewsCrawlResult crawlNews(@Valid @RequestBody NewsCrawlRequest request) {
        return crawlerService.crawlNews(request);
    }

    @PostMapping("/news/test")
    public NewsCrawlResult testNewsCrawler(@Valid @RequestBody NewsCrawlRequest request) {
        return crawlerService.crawlNews(request);
    }
}
