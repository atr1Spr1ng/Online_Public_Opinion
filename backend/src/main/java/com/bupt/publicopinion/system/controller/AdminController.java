package com.bupt.publicopinion.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bupt.publicopinion.collection.mapper.ArticleRawMapper;
import com.bupt.publicopinion.common.annotation.RequireAdmin;
import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.content.mapper.ArticleCleanMapper;
import com.bupt.publicopinion.event.mapper.EventMapper;
import com.bupt.publicopinion.system.entity.User;
import com.bupt.publicopinion.system.service.UserService;
import com.bupt.publicopinion.system.vo.UserInfo;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;
    private final ArticleRawMapper articleRawMapper;
    private final ArticleCleanMapper articleCleanMapper;
    private final EventMapper eventMapper;
    private final RestClient crawlerRestClient;
    private final RestClient contentRestClient;
    private final RestClient intelligenceRestClient;
    private final RestClient reportRestClient;
    private final ElasticsearchOperations elasticsearchOperations;
    private final Executor crawlTaskExecutor;

    public AdminController(
            UserService userService,
            ArticleRawMapper articleRawMapper,
            ArticleCleanMapper articleCleanMapper,
            EventMapper eventMapper,
            RestClient crawlerRestClient,
            RestClient contentRestClient,
            RestClient intelligenceRestClient,
            RestClient reportRestClient,
            ElasticsearchOperations elasticsearchOperations,
            Executor crawlTaskExecutor
    ) {
        this.userService = userService;
        this.articleRawMapper = articleRawMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.eventMapper = eventMapper;
        this.crawlerRestClient = crawlerRestClient;
        this.contentRestClient = contentRestClient;
        this.intelligenceRestClient = intelligenceRestClient;
        this.reportRestClient = reportRestClient;
        this.elasticsearchOperations = elasticsearchOperations;
        this.crawlTaskExecutor = crawlTaskExecutor;
    }

    @RequireAdmin
    @GetMapping("/users")
    public ApiResult<PageResult<UserInfo>> listUsers(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        PageResult<User> result = userService.listUsers(pageNum, pageSize);
        List<UserInfo> records = result.records().stream()
                .map(UserInfo::from)
                .toList();
        return ApiResult.success(new PageResult<>(records, result.total(), pageNum, pageSize));
    }

    @RequireAdmin
    @PutMapping("/users/{id}/status")
    public ApiResult<String> updateUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body
    ) {
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            return ApiResult.error(400, "状态值必须为 0 或 1");
        }
        // 禁止禁用 admin 账号
        if (id == 1L) {
            return ApiResult.error(400, "禁止禁用超级管理员账号");
        }
        userService.updateUserStatus(id, status);
        return ApiResult.success(status == 1 ? "已启用" : "已禁用");
    }

    @RequireAdmin
    @GetMapping("/stats")
    public ApiResult<Map<String, Object>> stats() {
        long totalUsers = userService.listUsers(1, 1).total();
        long totalArticles = articleRawMapper.selectCount(new LambdaQueryWrapper<>());
        long totalCleaned = articleCleanMapper.selectCount(new LambdaQueryWrapper<>());
        long totalEvents = eventMapper.selectCount(new LambdaQueryWrapper<>());

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        long todayArticles = articleRawMapper.selectCount(
                new LambdaQueryWrapper<com.bupt.publicopinion.collection.entity.ArticleRaw>()
                        .ge(com.bupt.publicopinion.collection.entity.ArticleRaw::getCreateTime, todayStart));

        long todayEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<com.bupt.publicopinion.event.entity.Event>()
                        .ge(com.bupt.publicopinion.event.entity.Event::getCreateTime, todayStart));

        long esArticleCount = countEsDocs("article_clean");
        long esEventCount = countEsDocs("events");

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("totalArticles", totalArticles);
        stats.put("totalCleaned", totalCleaned);
        stats.put("totalEvents", totalEvents);
        stats.put("todayArticles", todayArticles);
        stats.put("todayEvents", todayEvents);
        stats.put("esArticleCount", esArticleCount);
        stats.put("esEventCount", esEventCount);
        return ApiResult.success(stats);
    }

    @RequireAdmin
    @GetMapping("/services/health")
    public ApiResult<Map<String, Object>> servicesHealth() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 6 个服务状态
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("Java 后端", mapServiceStatus(true));
        services.put("Python Crawler (8001)", mapServiceStatus(checkService(crawlerRestClient)));
        services.put("Python Content (8002)", mapServiceStatus(checkService(contentRestClient)));
        services.put("Python Intelligence (8003)", mapServiceStatus(checkService(intelligenceRestClient)));
        services.put("Python Report (8004)", mapServiceStatus(checkService(reportRestClient)));
        services.put("Elasticsearch (9200)", mapEsStatus());
        result.put("services", services);

        // ES 索引详情
        Map<String, Long> esIndices = new LinkedHashMap<>();
        esIndices.put("article_clean", countEsDocs("article_clean"));
        esIndices.put("events", countEsDocs("events"));
        result.put("esIndices", esIndices);

        // 爬虫线程池状态
        if (crawlTaskExecutor instanceof ThreadPoolExecutor tpe) {
            Map<String, Object> pool = new LinkedHashMap<>();
            pool.put("corePoolSize", tpe.getCorePoolSize());
            pool.put("maximumPoolSize", tpe.getMaximumPoolSize());
            pool.put("activeCount", tpe.getActiveCount());
            pool.put("poolSize", tpe.getPoolSize());
            pool.put("queueSize", tpe.getQueue().size());
            pool.put("completedTaskCount", tpe.getCompletedTaskCount());
            result.put("crawlerThreadPool", pool);
        }

        return ApiResult.success(result);
    }

    // ---- helpers ----

    private boolean checkService(RestClient client) {
        try {
            client.get().uri("/internal/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> mapServiceStatus(boolean alive) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alive", alive);
        return m;
    }

    private Map<String, Object> mapEsStatus() {
        return mapServiceStatus(countEsDocs("article_clean") >= 0);
    }

    private long countEsDocs(String index) {
        try {
            return elasticsearchOperations.count(
                    new org.springframework.data.elasticsearch.core.query.CriteriaQuery(
                            new org.springframework.data.elasticsearch.core.query.Criteria()),
                    IndexCoordinates.of(index));
        } catch (Exception e) {
            return 0;
        }
    }
}
