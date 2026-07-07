package com.bupt.publicopinion.analysis.client;

import com.bupt.publicopinion.analysis.exception.IntelligenceServiceException;
import com.bupt.publicopinion.analysis.vo.SentimentResult;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class PythonIntelligenceClient {

    private final RestClient intelligenceRestClient;

    public PythonIntelligenceClient(RestClient intelligenceRestClient) {
        this.intelligenceRestClient = intelligenceRestClient;
    }

    public SentimentResult analyzeSentiment(String title, String content) {
        Map<String, String> body = Map.of(
                "title", title != null ? title : "",
                "content", content != null ? content : "",
                "language", "zh"
        );

        try {
            Map<?, ?> result = intelligenceRestClient.post()
                    .uri("/internal/analysis/sentiment")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (result == null) {
                throw new IntelligenceServiceException("Python 情感分析服务返回空响应");
            }

            String sentiment = (String) result.get("sentiment");
            BigDecimal positiveScore = toBigDecimal(result.get("positive_score"));
            BigDecimal negativeScore = toBigDecimal(result.get("negative_score"));
            BigDecimal confidence = toBigDecimal(result.get("confidence"));
            String details = (String) result.get("details");

            return new SentimentResult(null, null, sentiment, positiveScore, negativeScore, confidence, details, null);

        } catch (IntelligenceServiceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new IntelligenceServiceException("调用 Python 情感分析服务失败", e);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        return BigDecimal.ZERO;
    }
}
