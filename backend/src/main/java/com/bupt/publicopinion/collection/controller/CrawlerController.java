package com.bupt.publicopinion.collection.controller;

import com.bupt.publicopinion.collection.dto.NewsSourceRequest;
import com.bupt.publicopinion.collection.dto.NewsDiscoverRequest;
import com.bupt.publicopinion.collection.dto.NewsCrawlRequest;
import com.bupt.publicopinion.collection.dto.TopicSearchRequest;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.entity.CrawlTask;
import com.bupt.publicopinion.collection.entity.NewsSource;
import com.bupt.publicopinion.collection.service.CrawlerService;
import com.bupt.publicopinion.collection.vo.BatchCrawlerTaskResult;
import com.bupt.publicopinion.collection.vo.BatchTopicResult;
import com.bupt.publicopinion.collection.vo.CrawlTaskDetailResult;
import com.bupt.publicopinion.collection.vo.CrawlerHealthResult;
import com.bupt.publicopinion.collection.vo.CrawlerTaskSaveResult;
import com.bupt.publicopinion.collection.vo.NewsCollectResult;
import com.bupt.publicopinion.collection.vo.NewsDiscoverResult;
import com.bupt.publicopinion.collection.vo.NewsCrawlResult;
import com.bupt.publicopinion.collection.vo.SocialHotResult;
import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.common.vo.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
        requireAdmin();
        return crawlerService.checkHealth();
    }

    @PostMapping("/news/crawl")
    public ApiResult<ArticleRaw> crawlNews(@Valid @RequestBody NewsCrawlRequest request) {
        ArticleRaw articleRaw = crawlerService.crawlAndSaveArticle(request);
        return ApiResult.success(articleRaw);
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
    public ApiResult<CrawlerTaskSaveResult> createCrawlTask(@Valid @RequestBody NewsDiscoverRequest request) {
        requireAdmin();
        return ApiResult.success(crawlerService.createCrawlTask(request));
    }

    @PostMapping("/tasks/source/{sourceId}")
    public ApiResult<CrawlerTaskSaveResult> createCrawlTaskBySource(
            @PathVariable Long sourceId,
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return ApiResult.success(crawlerService.createCrawlTaskBySource(sourceId, limit));
    }

    @PostMapping("/tasks/all-enabled")
    public ApiResult<BatchCrawlerTaskResult> createCrawlTasksForAllEnabledSources(
            @RequestParam(defaultValue = "5") Integer limit
    ) {
        return ApiResult.success(crawlerService.createCrawlTasksForAllEnabledSources(limit));
    }

    @GetMapping("/tasks")
    public ApiResult<PageResult<CrawlTask>> listCrawlTasks(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        return ApiResult.success(crawlerService.listCrawlTasks(pageNum, pageSize));
    }

    @GetMapping("/tasks/{taskId}")
    public CrawlTaskDetailResult getCrawlTaskDetail(@PathVariable Long taskId) {
        return crawlerService.getCrawlTaskDetail(taskId);
    }

    @GetMapping("/articles")
    public ApiResult<PageResult<ArticleRaw>> listArticles(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(defaultValue = "false") boolean excludeCleaned
    ) {
        return ApiResult.success(crawlerService.listArticles(pageNum, pageSize, excludeCleaned));
    }

    @DeleteMapping("/articles/{id}")
    public ApiResult<Void> deleteArticle(@PathVariable Long id) {
        crawlerService.deleteArticle(id);
        return ApiResult.success();
    }

    @DeleteMapping("/tasks/{id}")
    public ApiResult<Void> deleteCrawlTask(@PathVariable Long id) {
        crawlerService.deleteCrawlTask(id);
        return ApiResult.success();
    }

    @GetMapping("/sources")
    public ApiResult<PageResult<NewsSource>> listNewsSources(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        return ApiResult.success(crawlerService.listNewsSources(pageNum, pageSize));
    }

    @PostMapping("/sources")
    public NewsSource createNewsSource(@Valid @RequestBody NewsSourceRequest request) {
        requireAdmin();
        return crawlerService.createNewsSource(request);
    }

    @PutMapping("/sources/{sourceId}")
    public NewsSource updateNewsSource(@PathVariable Long sourceId, @Valid @RequestBody NewsSourceRequest request) {
        requireAdmin();
        return crawlerService.updateNewsSource(sourceId, request);
    }

    @PostMapping("/sources/{sourceId}/status")
    public NewsSource updateNewsSourceStatus(
            @PathVariable Long sourceId,
            @RequestParam Integer status
    ) {
        requireAdmin();
        return crawlerService.updateNewsSourceStatus(sourceId, status);
    }

    @PostMapping("/news/test")
    public NewsCrawlResult testNewsCrawler(@Valid @RequestBody NewsCrawlRequest request) {
        return crawlerService.crawlNews(request);
    }

    @PostMapping("/topics/search")
    public ApiResult<BatchTopicResult> searchAndCollectByTopic(@Valid @RequestBody TopicSearchRequest request) {
        BatchTopicResult result = crawlerService.searchAndCollectByTopic(request);
        return ApiResult.success(result);
    }

    @GetMapping("/social/{platform}/hot")
    public ApiResult<SocialHotResult> fetchSocialHot(@PathVariable String platform) {
        return ApiResult.success(crawlerService.fetchSocialHot(platform));
    }

    private void requireAdmin() {
        String role = UserContext.get().role();
        if (!"ADMIN".equals(role)) {
            throw new com.bupt.publicopinion.common.exception.AccessDeniedException("无权访问");
        }
    }
}
