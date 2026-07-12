package com.bupt.publicopinion.analysis.controller;

import com.bupt.publicopinion.analysis.dto.SentimentRequest;
import com.bupt.publicopinion.analysis.entity.ArticleSentiment;
import com.bupt.publicopinion.analysis.service.AnalysisService;
import com.bupt.publicopinion.analysis.vo.SentimentResult;
import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.common.vo.PageResult;
import jakarta.validation.Valid;
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
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/health")
    public ApiResult<String> health() {
        return ApiResult.success("analysis service ok");
    }

    @PostMapping("/sentiment")
    public ApiResult<SentimentResult> analyzeSentiment(@Valid @RequestBody SentimentRequest request) {
        return ApiResult.success(analysisService.analyzeSentiment(request));
    }

    @PostMapping("/batch-sentiment")
    public ApiResult<List<SentimentResult>> batchAnalyze(@RequestBody List<Long> cleanIds) {
        return ApiResult.success(analysisService.batchAnalyze(cleanIds));
    }

    @GetMapping("/sentiment/{id}")
    public ApiResult<ArticleSentiment> getSentimentResult(@PathVariable Long id) {
        return ApiResult.success(analysisService.getSentimentResult(id));
    }

    @GetMapping("/sentiment")
    public ApiResult<PageResult<ArticleSentiment>> listSentimentResults(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String sentiment
    ) {
        return ApiResult.success(analysisService.listSentimentResults(pageNum, pageSize, sentiment));
    }

    @DeleteMapping("/sentiment/{id}")
    public ApiResult<Void> deleteSentimentResult(@PathVariable Long id) {
        analysisService.deleteSentimentResult(id);
        return ApiResult.success();
    }
}
