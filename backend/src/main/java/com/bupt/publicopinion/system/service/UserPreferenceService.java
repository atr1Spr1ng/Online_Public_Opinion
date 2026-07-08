package com.bupt.publicopinion.system.service;

import com.bupt.publicopinion.system.entity.UserDomain;
import com.bupt.publicopinion.system.entity.UserKeyword;
import com.bupt.publicopinion.system.vo.SourceSubscriptionVO;

import java.util.List;

public interface UserPreferenceService {

    // ── 关键词 ──
    List<UserKeyword> listKeywords(Long userId);
    UserKeyword addKeyword(Long userId, String keyword);
    void deleteKeyword(Long userId, Long keywordId);

    // ── 关注领域 ──
    List<UserDomain> listDomains(Long userId);
    UserDomain addDomain(Long userId, String domainName);
    void deleteDomain(Long userId, Long domainId);

    // ── 新闻源订阅 ──
    List<SourceSubscriptionVO> listSubscriptions(Long userId);
    void subscribe(Long userId, Long sourceId);
    void unsubscribe(Long userId, Long sourceId);
}
