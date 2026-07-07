package com.bupt.publicopinion.analysis.client;

import com.bupt.publicopinion.analysis.exception.IntelligenceServiceException;
import com.bupt.publicopinion.analysis.vo.SentimentResult;
import com.bupt.publicopinion.fake.exception.FakeDetectionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;
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

    @SuppressWarnings("unchecked")
    public FakeDetectionResult detectFake(String title, String content) {
        Map<String, String> body = Map.of(
                "title", title != null ? title : "",
                "content", content != null ? content : "",
                "language", "zh"
        );

        try {
            Map<?, ?> result = intelligenceRestClient.post()
                    .uri("/internal/analysis/fake-detection")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (result == null) {
                throw new FakeDetectionException("Python 虚假检测服务返回空响应");
            }

            double fakeScore = toDouble(result.get("fake_score"));
            boolean isFake = Boolean.TRUE.equals(result.get("is_fake"));
            Object methodObj = result.get("detection_method");
            String detectionMethod = methodObj instanceof String s ? s : "rule";
            Object featuresObj = result.get("features");
            String featuresJson = toFeaturesJson(featuresObj);
            Object detailsObj = result.get("details");
            String details = detailsObj instanceof String s ? s : "";

            return new FakeDetectionResult(fakeScore, isFake, detectionMethod, featuresJson, details);

        } catch (FakeDetectionException e) {
            throw e;
        } catch (RestClientException e) {
            throw new FakeDetectionException("调用 Python 虚假检测服务失败", e);
        }
    }

    private String toFeaturesJson(Object features) {
        if (features instanceof List<?> list && !list.isEmpty()) {
            try {
                // Simple JSON serialization of features list
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(list.get(i).toString());
                }
                sb.append("]");
                return sb.toString();
            } catch (Exception e) {
                return "[]";
            }
        }
        return "[]";
    }

    private double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number num) return num.doubleValue();
        return 0.0;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        return BigDecimal.ZERO;
    }

    public record FakeDetectionResult(
            double fakeScore,
            boolean isFake,
            String detectionMethod,
            String featuresJson,
            String details
    ) {}
}
