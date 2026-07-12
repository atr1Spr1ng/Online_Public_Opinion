package com.bupt.publicopinion.analysis.service;

import com.bupt.publicopinion.analysis.dto.SentimentRequest;
import com.bupt.publicopinion.analysis.entity.ArticleSentiment;
import com.bupt.publicopinion.analysis.vo.SentimentResult;
import com.bupt.publicopinion.common.vo.PageResult;

import java.util.List;

public interface AnalysisService {

    SentimentResult analyzeSentiment(SentimentRequest request);

    List<SentimentResult> batchAnalyze(List<Long> cleanIds);

    ArticleSentiment getSentimentResult(Long id);

    PageResult<ArticleSentiment> listSentimentResults(long pageNum, long pageSize, String sentiment);

    void deleteSentimentResult(Long id);
}
