package com.bupt.publicopinion.fake.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.analysis.client.PythonIntelligenceClient;
import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.mapper.ArticleCleanMapper;
import com.bupt.publicopinion.fake.dto.FakeDetectionRequest;
import com.bupt.publicopinion.fake.entity.ArticleFakeDetection;
import com.bupt.publicopinion.fake.exception.FakeDetectionException;
import com.bupt.publicopinion.fake.mapper.ArticleFakeDetectionMapper;
import com.bupt.publicopinion.fake.service.FakeDetectionService;
import com.bupt.publicopinion.fake.vo.FakeDetectionResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class FakeDetectionServiceImpl implements FakeDetectionService {

    private final ArticleFakeDetectionMapper articleFakeDetectionMapper;
    private final ArticleCleanMapper articleCleanMapper;
    private final PythonIntelligenceClient pythonIntelligenceClient;

    public FakeDetectionServiceImpl(
            ArticleFakeDetectionMapper articleFakeDetectionMapper,
            ArticleCleanMapper articleCleanMapper,
            PythonIntelligenceClient pythonIntelligenceClient
    ) {
        this.articleFakeDetectionMapper = articleFakeDetectionMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.pythonIntelligenceClient = pythonIntelligenceClient;
    }

    @Override
    public FakeDetectionResult detect(FakeDetectionRequest request) {
        ArticleClean clean = articleCleanMapper.selectById(request.cleanId());
        if (clean == null) {
            throw new FakeDetectionException("清洗后的文章不存在: " + request.cleanId());
        }

        // 删除旧结果，确保重新检测时不会重复
        articleFakeDetectionMapper.delete(
                new LambdaQueryWrapper<ArticleFakeDetection>()
                        .eq(ArticleFakeDetection::getCleanId, request.cleanId())
        );

        PythonIntelligenceClient.FakeDetectionResult pythonResult =
                pythonIntelligenceClient.detectFake(clean.getTitle(), clean.getContent());

        ArticleFakeDetection entity = saveResult(clean.getId(), pythonResult);
        return toVO(entity);
    }

    @Override
    public List<FakeDetectionResult> batchDetect(List<Long> cleanIds) {
        List<FakeDetectionResult> results = new ArrayList<>();
        for (Long cleanId : cleanIds) {
            try {
                results.add(detect(new FakeDetectionRequest(cleanId)));
            } catch (Exception e) {
                results.add(FakeDetectionResult.failed(cleanId, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    public FakeDetectionResult getResult(Long id) {
        ArticleFakeDetection entity = articleFakeDetectionMapper.selectById(id);
        if (entity == null) {
            throw new FakeDetectionException("虚假检测结果不存在: " + id);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<FakeDetectionResult> listResults(long pageNum, long pageSize, Boolean isFake) {
        Page<ArticleFakeDetection> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ArticleFakeDetection> wrapper = new LambdaQueryWrapper<>();
        if (isFake != null) {
            wrapper.eq(ArticleFakeDetection::getIsFake, isFake ? 1 : 0);
        }
        wrapper.orderByDesc(ArticleFakeDetection::getCreateTime);
        if (!"ADMIN".equals(UserContext.get().role())) {
            wrapper.eq(ArticleFakeDetection::getUserId, UserContext.get().userId());
        }
        Page<ArticleFakeDetection> result = articleFakeDetectionMapper.selectPage(page, wrapper);
        List<FakeDetectionResult> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public void deleteFakeResult(Long id) {
        ArticleFakeDetection entity = articleFakeDetectionMapper.selectById(id);
        if (entity == null) {
            throw new FakeDetectionException("虚假检测结果不存在: " + id);
        }
        articleFakeDetectionMapper.deleteById(id);
    }

    private ArticleFakeDetection saveResult(Long cleanId, PythonIntelligenceClient.FakeDetectionResult result) {
        ArticleFakeDetection entity = new ArticleFakeDetection();
        entity.setCleanId(cleanId);
        entity.setFakeScore(BigDecimal.valueOf(result.fakeScore()));
        entity.setIsFake(result.isFake() ? 1 : 0);
        entity.setDetectionMethod(result.detectionMethod());
        entity.setFeaturesJson(result.featuresJson());
        entity.setDetails(result.details());
        entity.setUserId(UserContext.get().userId());
        articleFakeDetectionMapper.insert(entity);
        return entity;
    }

    private FakeDetectionResult toVO(ArticleFakeDetection entity) {
        return new FakeDetectionResult(
                entity.getId(),
                entity.getCleanId(),
                entity.getFakeScore(),
                entity.getIsFake() != null && entity.getIsFake() == 1,
                entity.getDetectionMethod(),
                entity.getFeaturesJson(),
                entity.getDetails(),
                entity.getCreateTime()
        );
    }
}
