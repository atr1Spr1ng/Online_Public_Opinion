package com.bupt.publicopinion.content.controller;

import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.content.dto.CleanRequest;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.service.ContentService;
import com.bupt.publicopinion.content.vo.CleanResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/content")
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/health")
    public ApiResult<String> health() {
        return ApiResult.success("content service ok");
    }

    @PostMapping("/clean")
    public ApiResult<CleanResult> cleanArticle(@RequestBody CleanRequest request) {
        return ApiResult.success(contentService.cleanArticle(request));
    }

    @PostMapping("/batch-clean")
    public ApiResult<List<CleanResult>> batchClean(@RequestBody List<Long> rawIds) {
        return ApiResult.success(contentService.batchClean(rawIds));
    }

    @GetMapping("/clean/{id}")
    public ApiResult<ArticleClean> getCleanedArticle(@PathVariable Long id) {
        return ApiResult.success(contentService.getCleanedArticle(id));
    }

    @GetMapping("/clean")
    public ApiResult<PageResult<ArticleClean>> listCleanedArticles(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(defaultValue = "false") boolean excludeAnalyzed,
            @RequestParam(defaultValue = "false") boolean excludeDetected
    ) {
        return ApiResult.success(contentService.listCleanedArticles(pageNum, pageSize, excludeAnalyzed, excludeDetected));
    }

    @DeleteMapping("/clean/{id}")
    public ApiResult<Void> deleteCleanedArticle(@PathVariable Long id) {
        contentService.deleteCleanedArticle(id);
        return ApiResult.success();
    }
}
