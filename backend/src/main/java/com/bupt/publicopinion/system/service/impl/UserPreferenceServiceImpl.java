package com.bupt.publicopinion.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bupt.publicopinion.collection.entity.NewsSource;
import com.bupt.publicopinion.collection.mapper.NewsSourceMapper;
import com.bupt.publicopinion.system.entity.UserDomain;
import com.bupt.publicopinion.system.entity.UserKeyword;
import com.bupt.publicopinion.system.entity.UserSourceSubscription;
import com.bupt.publicopinion.system.mapper.UserDomainMapper;
import com.bupt.publicopinion.system.mapper.UserKeywordMapper;
import com.bupt.publicopinion.system.mapper.UserSourceSubscriptionMapper;
import com.bupt.publicopinion.system.service.UserPreferenceService;
import com.bupt.publicopinion.system.vo.SourceSubscriptionVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private final UserKeywordMapper userKeywordMapper;
    private final UserDomainMapper userDomainMapper;
    private final UserSourceSubscriptionMapper userSourceSubscriptionMapper;
    private final NewsSourceMapper newsSourceMapper;

    public UserPreferenceServiceImpl(
            UserKeywordMapper userKeywordMapper,
            UserDomainMapper userDomainMapper,
            UserSourceSubscriptionMapper userSourceSubscriptionMapper,
            NewsSourceMapper newsSourceMapper
    ) {
        this.userKeywordMapper = userKeywordMapper;
        this.userDomainMapper = userDomainMapper;
        this.userSourceSubscriptionMapper = userSourceSubscriptionMapper;
        this.newsSourceMapper = newsSourceMapper;
    }

    // ── 关键词 ──

    @Override
    public List<UserKeyword> listKeywords(Long userId) {
        return userKeywordMapper.selectList(
                new LambdaQueryWrapper<UserKeyword>()
                        .eq(UserKeyword::getUserId, userId)
                        .orderByDesc(UserKeyword::getCreateTime)
        );
    }

    @Override
    public UserKeyword addKeyword(Long userId, String keyword) {
        Long count = userKeywordMapper.selectCount(
                new LambdaQueryWrapper<UserKeyword>()
                        .eq(UserKeyword::getUserId, userId)
                        .eq(UserKeyword::getKeyword, keyword)
        );
        if (count > 0) {
            return userKeywordMapper.selectOne(
                    new LambdaQueryWrapper<UserKeyword>()
                            .eq(UserKeyword::getUserId, userId)
                            .eq(UserKeyword::getKeyword, keyword)
            );
        }

        UserKeyword uk = new UserKeyword();
        uk.setUserId(userId);
        uk.setKeyword(keyword);
        userKeywordMapper.insert(uk);
        return uk;
    }

    @Override
    public void deleteKeyword(Long userId, Long keywordId) {
        userKeywordMapper.delete(
                new LambdaQueryWrapper<UserKeyword>()
                        .eq(UserKeyword::getId, keywordId)
                        .eq(UserKeyword::getUserId, userId)
        );
    }

    // ── 关注领域 ──

    @Override
    public List<UserDomain> listDomains(Long userId) {
        return userDomainMapper.selectList(
                new LambdaQueryWrapper<UserDomain>()
                        .eq(UserDomain::getUserId, userId)
                        .orderByDesc(UserDomain::getCreateTime)
        );
    }

    @Override
    public UserDomain addDomain(Long userId, String domainName) {
        Long count = userDomainMapper.selectCount(
                new LambdaQueryWrapper<UserDomain>()
                        .eq(UserDomain::getUserId, userId)
                        .eq(UserDomain::getDomainName, domainName)
        );
        if (count > 0) {
            return userDomainMapper.selectOne(
                    new LambdaQueryWrapper<UserDomain>()
                            .eq(UserDomain::getUserId, userId)
                            .eq(UserDomain::getDomainName, domainName)
            );
        }

        UserDomain ud = new UserDomain();
        ud.setUserId(userId);
        ud.setDomainName(domainName);
        userDomainMapper.insert(ud);
        return ud;
    }

    @Override
    public void deleteDomain(Long userId, Long domainId) {
        userDomainMapper.delete(
                new LambdaQueryWrapper<UserDomain>()
                        .eq(UserDomain::getId, domainId)
                        .eq(UserDomain::getUserId, userId)
        );
    }

    // ── 新闻源订阅 ──

    @Override
    public List<SourceSubscriptionVO> listSubscriptions(Long userId) {
        // 获取用户订阅记录
        List<UserSourceSubscription> subs = userSourceSubscriptionMapper.selectList(
                new LambdaQueryWrapper<UserSourceSubscription>()
                        .eq(UserSourceSubscription::getUserId, userId)
        );
        List<Long> subscribedIds = subs.stream()
                .map(UserSourceSubscription::getSourceId).toList();

        // 获取所有启用的新闻源
        List<NewsSource> allSources = newsSourceMapper.selectList(
                new LambdaQueryWrapper<NewsSource>()
                        .eq(NewsSource::getStatus, 1)
        );

        // 组装结果
        List<SourceSubscriptionVO> vos = new ArrayList<>();
        for (NewsSource source : allSources) {
            UserSourceSubscription sub = subs.stream()
                    .filter(s -> s.getSourceId().equals(source.getId()))
                    .findFirst().orElse(null);
            vos.add(SourceSubscriptionVO.from(source, sub));
        }
        return vos;
    }

    @Override
    public void subscribe(Long userId, Long sourceId) {
        // 检查是否已订阅
        Long count = userSourceSubscriptionMapper.selectCount(
                new LambdaQueryWrapper<UserSourceSubscription>()
                        .eq(UserSourceSubscription::getUserId, userId)
                        .eq(UserSourceSubscription::getSourceId, sourceId)
        );
        if (count > 0) return;

        UserSourceSubscription sub = new UserSourceSubscription();
        sub.setUserId(userId);
        sub.setSourceId(sourceId);
        userSourceSubscriptionMapper.insert(sub);
    }

    @Override
    public void unsubscribe(Long userId, Long sourceId) {
        userSourceSubscriptionMapper.delete(
                new LambdaQueryWrapper<UserSourceSubscription>()
                        .eq(UserSourceSubscription::getUserId, userId)
                        .eq(UserSourceSubscription::getSourceId, sourceId)
        );
    }
}
