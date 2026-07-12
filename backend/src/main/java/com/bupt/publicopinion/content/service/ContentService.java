package com.bupt.publicopinion.content.service;

import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.content.dto.CleanRequest;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.vo.CleanResult;

import java.util.List;

public interface ContentService {

    CleanResult cleanArticle(CleanRequest request);

    List<CleanResult> batchClean(List<Long> rawIds);

    ArticleClean getCleanedArticle(Long id);

    PageResult<ArticleClean> listCleanedArticles(long pageNum, long pageSize, boolean excludeAnalyzed, boolean excludeDetected);

    void deleteCleanedArticle(Long id);
}
