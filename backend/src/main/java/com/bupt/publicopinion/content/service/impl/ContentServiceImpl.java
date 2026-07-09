package com.bupt.publicopinion.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.mapper.ArticleRawMapper;
import com.bupt.publicopinion.content.client.PythonContentClient;
import com.bupt.publicopinion.content.dto.CleanRequest;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.exception.ContentServiceException;
import com.bupt.publicopinion.content.mapper.ArticleCleanMapper;
import com.bupt.publicopinion.content.service.ContentService;
import com.bupt.publicopinion.content.vo.CleanResult;
import com.bupt.publicopinion.search.service.SearchSyncService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContentServiceImpl implements ContentService {

    private final ArticleCleanMapper articleCleanMapper;
    private final ArticleRawMapper articleRawMapper;
    private final PythonContentClient pythonContentClient;
    private final SearchSyncService searchSyncService;

    public ContentServiceImpl(
            ArticleCleanMapper articleCleanMapper,
            ArticleRawMapper articleRawMapper,
            PythonContentClient pythonContentClient,
            SearchSyncService searchSyncService
    ) {
        this.articleCleanMapper = articleCleanMapper;
        this.articleRawMapper = articleRawMapper;
        this.pythonContentClient = pythonContentClient;
        this.searchSyncService = searchSyncService;
    }

    @Override
    public CleanResult cleanArticle(CleanRequest request) {
        ArticleRaw raw = articleRawMapper.selectById(request.rawId());
        if (raw == null) {
            throw new ContentServiceException("原始文章不存在: " + request.rawId());
        }

        CleanResult result = pythonContentClient.clean(raw);
        saveCleanResult(raw, result);
        return result;
    }

    @Override
    public List<CleanResult> batchClean(List<Long> rawIds) {
        List<CleanResult> results = new ArrayList<>();
        for (Long rawId : rawIds) {
            try {
                results.add(cleanArticle(new CleanRequest(rawId)));
            } catch (Exception e) {
                results.add(CleanResult.failed(rawId, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    public ArticleClean getCleanedArticle(Long id) {
        ArticleClean article = articleCleanMapper.selectById(id);
        if (article == null) {
            throw new ContentServiceException("清洗后的文章不存在: " + id);
        }
        return article;
    }

    @Override
    public List<ArticleClean> listCleanedArticles(long pageNum, long pageSize) {
        Page<ArticleClean> page = new Page<>(pageNum, pageSize);
        Page<ArticleClean> result = articleCleanMapper.selectPage(
                page,
                new LambdaQueryWrapper<ArticleClean>()
                        .orderByDesc(ArticleClean::getCreateTime)
        );
        return result.getRecords();
    }

    private void saveCleanResult(ArticleRaw raw, CleanResult result) {
        ArticleClean clean = new ArticleClean();
        clean.setRawId(raw.getId());
        clean.setTitle(result.title());
        clean.setContent(result.content());
        clean.setKeywords(result.keywords());
        clean.setSummary(result.summary());
        clean.setLanguage(result.language());
        clean.setPublishedAt(raw.getPublishedAt());
        clean.setSourceName(raw.getSourceName());
        clean.setStatus(result.status());

        // ES more_like_this 内容去重
        boolean isDuplicate = false;
        try {
            var similar = searchSyncService.findSimilar(result.title(), result.content(), 1);
            isDuplicate = !similar.isEmpty();
        } catch (Exception e) {
            // ES 不可用时不阻断清洗流程
        }

        clean.setSimhash(isDuplicate ? -1L : 0L);
        articleCleanMapper.insert(clean);
    }
}
