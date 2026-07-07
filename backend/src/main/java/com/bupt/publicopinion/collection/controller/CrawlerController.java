package com.bupt.publicopinion.collection.controller;

import com.bupt.publicopinion.collection.dto.NewsSourceRequest;
import com.bupt.publicopinion.collection.dto.NewsDiscoverRequest;
import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.entity.CrawlTask;
import com.bupt.publicopinion.collection.entity.NewsSource;
import com.bupt.publicopinion.collection.service.CrawlerService;
import com.bupt.publicopinion.collection.vo.CrawlTaskDetailResult;
import com.bupt.publicopinion.collection.vo.CrawlerHealthResult;
import com.bupt.publicopinion.collection.vo.CrawlerTaskSaveResult;
import com.bupt.publicopinion.collection.vo.NewsCollectResult;
import com.bupt.publicopinion.collection.vo.NewsDiscoverResult;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;
import com.bupt.publicopinion.common.vo.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @PostMapping("/news/discover")
    public NewsDiscoverResult discoverNewsLinks(@Valid @RequestBody NewsDiscoverRequest request) {
        return crawlerService.discoverNewsLinks(request);
    }

    @PostMapping("/news/collect")
    public NewsCollectResult collectNews(@Valid @RequestBody NewsDiscoverRequest request) {
        return crawlerService.collectNews(request);
    }

    @PostMapping("/tasks")
    public CrawlerTaskSaveResult createCrawlTask(@Valid @RequestBody NewsDiscoverRequest request) {
        return crawlerService.createCrawlTask(request);
    }

    @GetMapping("/tasks")
    public PageResult<CrawlTask> listCrawlTasks(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        return crawlerService.listCrawlTasks(pageNum, pageSize);
    }

    @GetMapping("/tasks/{taskId}")
    public CrawlTaskDetailResult getCrawlTaskDetail(@PathVariable Long taskId) {
        return crawlerService.getCrawlTaskDetail(taskId);
    }

    @GetMapping("/articles")
    public PageResult<ArticleRaw> listArticles(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        return crawlerService.listArticles(pageNum, pageSize);
    }

    @GetMapping("/sources")
    public PageResult<NewsSource> listNewsSources(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        return crawlerService.listNewsSources(pageNum, pageSize);
    }

    @PostMapping("/sources")
    public NewsSource createNewsSource(@Valid @RequestBody NewsSourceRequest request) {
        return crawlerService.createNewsSource(request);
    }

    @PostMapping("/news/test")
    public NewsCrawlResult testNewsCrawler(@Valid @RequestBody NewsCrawlRequest request) {
        return crawlerService.crawlNews(request);
    }
}
