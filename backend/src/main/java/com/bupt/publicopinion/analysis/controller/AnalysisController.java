package com.bupt.publicopinion.analysis.controller;

import com.bupt.publicopinion.analysis.dto.SentimentRequest;
import com.bupt.publicopinion.analysis.entity.ArticleSentiment;
import com.bupt.publicopinion.analysis.service.AnalysisService;
import com.bupt.publicopinion.analysis.vo.SentimentResult;
import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.task.entity.ProcessingTask;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;

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
    public ApiResult<ProcessingTask> batchAnalyze(@RequestBody JsonNode body) {
        return ApiResult.success(analysisService.batchAnalyze(parseCleanIds(body), parseMode(body)));
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

    private List<Long> parseCleanIds(JsonNode body) {
        JsonNode idsNode = body != null && body.isObject() ? body.get("cleanIds") : body;
        List<Long> ids = new ArrayList<>();
        if (idsNode != null && idsNode.isArray()) {
            idsNode.forEach(node -> {
                if (node.canConvertToLong()) ids.add(node.asLong());
            });
        }
        return ids;
    }

    private String parseMode(JsonNode body) {
        if (body != null && body.isObject() && body.hasNonNull("mode")) {
            return body.get("mode").asText();
        }
        return null;
    }
}
