package com.bupt.publicopinion.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.mapper.ArticleRawMapper;
import com.bupt.publicopinion.common.annotation.RequireAdmin;
import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.mapper.ArticleCleanMapper;
import com.bupt.publicopinion.event.entity.Event;
import com.bupt.publicopinion.event.mapper.EventMapper;
import com.bupt.publicopinion.system.entity.User;
import com.bupt.publicopinion.system.service.UserService;
import com.bupt.publicopinion.system.vo.AdminArticleItem;
import com.bupt.publicopinion.system.vo.AdminEventItem;
import com.bupt.publicopinion.system.vo.UserInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.ArrayList;
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
    private final ObjectMapper objectMapper;
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
            ObjectMapper objectMapper,
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
        this.objectMapper = objectMapper;
        this.crawlTaskExecutor = crawlTaskExecutor;
    }

    // ────────── 用户管理 ──────────

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
        Long currentUserId = UserContext.get().userId();
        if (id.equals(currentUserId)) {
            return ApiResult.error(400, "禁止操作自己的账号");
        }
        if (id == 1L) {
            return ApiResult.error(400, "禁止禁用超级管理员账号");
        }
        userService.updateUserStatus(id, status);
        return ApiResult.success(status == 1 ? "已启用" : "已禁用");
    }

    @RequireAdmin
    @PutMapping("/users/{id}")
    public ApiResult<String> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        Long currentUserId = UserContext.get().userId();
        String role = body.get("role");
        if (role != null && id.equals(currentUserId)) {
            return ApiResult.error(400, "禁止修改自己的角色");
        }
        if (role != null && id == 1L) {
            return ApiResult.error(400, "禁止修改超级管理员角色");
        }
        userService.updateUser(id, body.get("nickname"), body.get("email"), role);
        return ApiResult.success("ok");
    }

    @RequireAdmin
    @PutMapping("/users/{id}/reset-password")
    public ApiResult<String> resetPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String password = body.get("password");
        if (password == null || password.length() < 6) {
            return ApiResult.error(400, "密码至少6位");
        }
        userService.resetPassword(id, password);
        return ApiResult.success("密码已重置");
    }

    @RequireAdmin
    @DeleteMapping("/users/{id}")
    public ApiResult<String> deleteUser(@PathVariable Long id) {
        Long currentUserId = UserContext.get().userId();
        if (id.equals(currentUserId)) {
            return ApiResult.error(400, "禁止删除自己的账号");
        }
        if (id == 1L) {
            return ApiResult.error(400, "禁止删除超级管理员账号");
        }
        userService.deleteUser(id);
        return ApiResult.success("已删除");
    }

    // ────────── 平台统计 ──────────

    @RequireAdmin
    @GetMapping("/stats")
    public ApiResult<Map<String, Object>> stats() {
        long totalUsers = userService.listUsers(1, 1).total();
        long totalArticles = articleRawMapper.selectCount(new LambdaQueryWrapper<>());
        long totalCleaned = articleCleanMapper.selectCount(new LambdaQueryWrapper<>());
        long totalEvents = eventMapper.selectCount(new LambdaQueryWrapper<>());

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        long todayArticles = articleRawMapper.selectCount(
                new LambdaQueryWrapper<ArticleRaw>()
                        .ge(ArticleRaw::getCreateTime, todayStart));
        long todayEvents = eventMapper.selectCount(
                new LambdaQueryWrapper<Event>()
                        .ge(Event::getCreateTime, todayStart));

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

    // ────────── 服务健康 ──────────

    @RequireAdmin
    @GetMapping("/services/health")
    public ApiResult<Map<String, Object>> servicesHealth() {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("Java 后端", mapServiceStatus(true));
        services.put("Python Crawler (8001)", mapServiceStatus(checkService(crawlerRestClient)));
        services.put("Python Content (8002)", mapServiceStatus(checkService(contentRestClient)));
        services.put("Python Intelligence (8003)", mapServiceStatus(checkService(intelligenceRestClient)));
        services.put("Python Report (8004)", mapServiceStatus(checkService(reportRestClient)));
        services.put("Elasticsearch (9200)", mapEsStatus());
        result.put("services", services);

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

    // ────────── 数据明细（可下钻） ──────────

    @RequireAdmin
    @GetMapping("/articles")
    public ApiResult<PageResult<AdminArticleItem>> listArticles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        LambdaQueryWrapper<ArticleRaw> wrapper = new LambdaQueryWrapper<ArticleRaw>()
                .orderByDesc(ArticleRaw::getCreateTime)
                .ge(dateFrom != null && !dateFrom.isBlank(), ArticleRaw::getCreateTime, dateFrom)
                .le(dateTo != null && !dateTo.isBlank(), ArticleRaw::getCreateTime, dateTo)
                .select(ArticleRaw::getId, ArticleRaw::getTitle, ArticleRaw::getSourceName, ArticleRaw::getCreateTime);
        Page<ArticleRaw> page = new Page<>(pageNum, pageSize);
        IPage<ArticleRaw> result = articleRawMapper.selectPage(page, wrapper);

        List<AdminArticleItem> items = result.getRecords().stream().map(AdminArticleItem::fromRaw).toList();
        return ApiResult.success(new PageResult<>(items, result.getTotal(), pageNum, pageSize));
    }

    @RequireAdmin
    @GetMapping("/cleaned-articles")
    public ApiResult<PageResult<AdminArticleItem>> listCleanedArticles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Page<ArticleClean> page = new Page<>(pageNum, pageSize);
        IPage<ArticleClean> result = articleCleanMapper.selectPage(page,
                new LambdaQueryWrapper<ArticleClean>()
                        .orderByDesc(ArticleClean::getCreateTime)
                        .select(ArticleClean::getId, ArticleClean::getTitle, ArticleClean::getSourceName, ArticleClean::getCreateTime));

        List<AdminArticleItem> items = result.getRecords().stream().map(AdminArticleItem::fromClean).toList();
        return ApiResult.success(new PageResult<>(items, result.getTotal(), pageNum, pageSize));
    }

    @RequireAdmin
    @GetMapping("/events")
    public ApiResult<PageResult<AdminEventItem>> listEvents(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        LambdaQueryWrapper<Event> wrapper = new LambdaQueryWrapper<Event>()
                .orderByDesc(Event::getCreateTime)
                .ge(dateFrom != null && !dateFrom.isBlank(), Event::getCreateTime, dateFrom)
                .le(dateTo != null && !dateTo.isBlank(), Event::getCreateTime, dateTo);
        Page<Event> page = new Page<>(pageNum, pageSize);
        IPage<Event> result = eventMapper.selectPage(page, wrapper);

        List<AdminEventItem> items = result.getRecords().stream().map(AdminEventItem::from).toList();
        return ApiResult.success(new PageResult<>(items, result.getTotal(), pageNum, pageSize));
    }

    @RequireAdmin
    @GetMapping("/es/articles")
    public ApiResult<PageResult<Map<String, Object>>> listEsArticles(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return queryEsIndex("article_clean", pageNum, pageSize);
    }

    @RequireAdmin
    @GetMapping("/es/events")
    public ApiResult<PageResult<Map<String, Object>>> listEsEvents(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return queryEsIndex("events", pageNum, pageSize);
    }

    // ---- helpers ----

    private ApiResult<PageResult<Map<String, Object>>> queryEsIndex(String index, int pageNum, int pageSize) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.putObject("query").putObject("match_all");
            body.put("from", (pageNum - 1) * pageSize);
            body.put("size", pageSize);
            body.putArray("sort").addObject().put("_score", "desc");

            String response = RestClient.create()
                    .post()
                    .uri("http://127.0.0.1:9200/" + index + "/_search")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode hits = root.path("hits");
            long total = hits.path("total").path("value").asLong();

            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode hit : hits.path("hits")) {
                Map<String, Object> item = new LinkedHashMap<>();
                JsonNode source = hit.path("_source");
                var fields = source.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    JsonNode value = entry.getValue();
                    if (value.isTextual()) {
                        item.put(entry.getKey(), value.asText());
                    } else if (value.isNumber()) {
                        item.put(entry.getKey(), value.numberValue());
                    } else if (value.isBoolean()) {
                        item.put(entry.getKey(), value.asBoolean());
                    } else {
                        item.put(entry.getKey(), value.toString());
                    }
                }
                item.put("_id", hit.path("_id").asText());
                items.add(item);
            }

            return ApiResult.success(new PageResult<>(items, total, pageNum, pageSize));
        } catch (Exception e) {
            return ApiResult.success(new PageResult<>(List.of(), 0, pageNum, pageSize));
        }
    }

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
            String response = RestClient.create()
                    .post()
                    .uri("http://127.0.0.1:9200/" + index + "/_count")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body("{\"query\":{\"match_all\":{}}}")
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response).path("count").asLong();
        } catch (Exception e) {
            return 0;
        }
    }
}
