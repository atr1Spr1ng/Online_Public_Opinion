package com.bupt.publicopinion.system.controller;

import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.result.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final RestClient crawlerRestClient;
    private final RestClient contentRestClient;
    private final RestClient intelligenceRestClient;
    private final RestClient reportRestClient;

    public SystemController(
            RestClient crawlerRestClient,
            RestClient contentRestClient,
            RestClient intelligenceRestClient,
            RestClient reportRestClient
    ) {
        this.crawlerRestClient = crawlerRestClient;
        this.contentRestClient = contentRestClient;
        this.intelligenceRestClient = intelligenceRestClient;
        this.reportRestClient = reportRestClient;
    }

    @GetMapping("/health")
    public ApiResult<Map<String, Boolean>> health() {
        String role = UserContext.get().role();
        if (!"ADMIN".equals(role)) {
            return ApiResult.error(403, "无权访问");
        }

        Map<String, Boolean> status = new LinkedHashMap<>();
        status.put("Java 后端", true);
        status.put("Python Crawler (8001)", checkService(crawlerRestClient));
        status.put("Python Content (8002)", checkService(contentRestClient));
        status.put("Python Intelligence (8003)", checkService(intelligenceRestClient));
        status.put("Python Report (8004)", checkService(reportRestClient));

        return ApiResult.success(status);
    }

    private boolean checkService(RestClient client) {
        try {
            client.get().uri("/internal/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
