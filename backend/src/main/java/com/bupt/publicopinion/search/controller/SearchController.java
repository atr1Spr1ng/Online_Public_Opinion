package com.bupt.publicopinion.search.controller;

import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.search.document.ArticleDocument;
import com.bupt.publicopinion.search.service.SearchSyncService;
import com.bupt.publicopinion.system.entity.UserKeyword;
import com.bupt.publicopinion.system.service.UserPreferenceService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchSyncService searchSyncService;
    private final UserPreferenceService userPreferenceService;

    public SearchController(SearchSyncService searchSyncService, UserPreferenceService userPreferenceService) {
        this.searchSyncService = searchSyncService;
        this.userPreferenceService = userPreferenceService;
    }

    /**
     * 关键词搜索文章（全文检索）
     */
    @GetMapping("/articles")
    public ApiResult<PageResult<ArticleDocument>> searchArticles(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Page<ArticleDocument> page;
        if (keyword != null && !keyword.isBlank()) {
            page = searchSyncService.searchByKeyword(keyword, pageNum, pageSize);
        } else {
            page = searchSyncService.findAll(pageNum, pageSize);
        }
        return ApiResult.success(new PageResult<>(
                page.getContent(), page.getTotalElements(),
                pageNum, pageSize
        ));
    }

    /**
     * 按当前用户偏好关键词过滤文章（取用户设置的所有关键词做 ES multi_match）
     */
    @GetMapping("/articles/my-keywords")
    public ApiResult<PageResult<ArticleDocument>> searchByMyKeywords(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Long userId = UserContext.get().userId();
        List<UserKeyword> keywords = userPreferenceService.listKeywords(userId);

        if (keywords.isEmpty()) {
            // 未设置关键词时返回空列表
            return ApiResult.success(new PageResult<>(List.of(), 0L, pageNum, pageSize));
        }

        List<String> keywordStrs = keywords.stream().map(UserKeyword::getKeyword).toList();
        Page<ArticleDocument> page = searchSyncService.searchByKeywords(keywordStrs, pageNum, pageSize);
        return ApiResult.success(new PageResult<>(
                page.getContent(), page.getTotalElements(),
                pageNum, pageSize
        ));
    }

    /**
     * 按关键词 + 来源过滤
     */
    @GetMapping("/articles/filter")
    public ApiResult<PageResult<ArticleDocument>> searchByKeywordAndSource(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String sourceName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        Page<ArticleDocument> page = searchSyncService.searchByKeywordAndSource(
                keyword, sourceName, pageNum, pageSize
        );
        return ApiResult.success(new PageResult<>(
                page.getContent(), page.getTotalElements(),
                pageNum, pageSize
        ));
    }
}
