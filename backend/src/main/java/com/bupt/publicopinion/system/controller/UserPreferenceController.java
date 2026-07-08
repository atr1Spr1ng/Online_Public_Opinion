package com.bupt.publicopinion.system.controller;

import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.system.dto.DomainRequest;
import com.bupt.publicopinion.system.dto.KeywordRequest;
import com.bupt.publicopinion.system.entity.UserDomain;
import com.bupt.publicopinion.system.entity.UserKeyword;
import com.bupt.publicopinion.system.service.UserPreferenceService;
import com.bupt.publicopinion.system.vo.DomainVO;
import com.bupt.publicopinion.system.vo.KeywordVO;
import com.bupt.publicopinion.system.vo.SourceSubscriptionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    public UserPreferenceController(UserPreferenceService userPreferenceService) {
        this.userPreferenceService = userPreferenceService;
    }

    // ── 关键词 ──

    @GetMapping("/keywords")
    public ApiResult<List<KeywordVO>> listKeywords() {
        Long userId = UserContext.get().userId();
        List<UserKeyword> keywords = userPreferenceService.listKeywords(userId);
        return ApiResult.success(keywords.stream().map(KeywordVO::from).toList());
    }

    @PostMapping("/keywords")
    public ApiResult<KeywordVO> addKeyword(@Valid @RequestBody KeywordRequest request) {
        Long userId = UserContext.get().userId();
        UserKeyword uk = userPreferenceService.addKeyword(userId, request.keyword());
        return ApiResult.success(KeywordVO.from(uk));
    }

    @DeleteMapping("/keywords/{id}")
    public ApiResult<Void> deleteKeyword(@PathVariable Long id) {
        Long userId = UserContext.get().userId();
        userPreferenceService.deleteKeyword(userId, id);
        return ApiResult.success(null);
    }

    // ── 关注领域 ──

    @GetMapping("/domains")
    public ApiResult<List<DomainVO>> listDomains() {
        Long userId = UserContext.get().userId();
        List<UserDomain> domains = userPreferenceService.listDomains(userId);
        return ApiResult.success(domains.stream().map(DomainVO::from).toList());
    }

    @PostMapping("/domains")
    public ApiResult<DomainVO> addDomain(@Valid @RequestBody DomainRequest request) {
        Long userId = UserContext.get().userId();
        UserDomain ud = userPreferenceService.addDomain(userId, request.domainName());
        return ApiResult.success(DomainVO.from(ud));
    }

    @DeleteMapping("/domains/{id}")
    public ApiResult<Void> deleteDomain(@PathVariable Long id) {
        Long userId = UserContext.get().userId();
        userPreferenceService.deleteDomain(userId, id);
        return ApiResult.success(null);
    }

    // ── 新闻源订阅 ──

    @GetMapping("/sources")
    public ApiResult<List<SourceSubscriptionVO>> listSources() {
        Long userId = UserContext.get().userId();
        return ApiResult.success(userPreferenceService.listSubscriptions(userId));
    }

    @PostMapping("/sources/{sourceId}")
    public ApiResult<Void> subscribe(@PathVariable Long sourceId) {
        Long userId = UserContext.get().userId();
        userPreferenceService.subscribe(userId, sourceId);
        return ApiResult.success(null);
    }

    @DeleteMapping("/sources/{sourceId}")
    public ApiResult<Void> unsubscribe(@PathVariable Long sourceId) {
        Long userId = UserContext.get().userId();
        userPreferenceService.unsubscribe(userId, sourceId);
        return ApiResult.success(null);
    }
}
