package com.bupt.publicopinion.analysis.client;

import com.bupt.publicopinion.analysis.exception.IntelligenceServiceException;
import com.bupt.publicopinion.analysis.vo.SentimentResult;
import com.bupt.publicopinion.fake.exception.FakeDetectionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PythonIntelligenceClient {

    private final RestClient intelligenceRestClient;
    private final ObjectMapper objectMapper;

    public PythonIntelligenceClient(RestClient intelligenceRestClient, ObjectMapper objectMapper) {
        this.intelligenceRestClient = intelligenceRestClient;
        this.objectMapper = objectMapper;
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
                return objectMapper.writeValueAsString(list);
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
    @SuppressWarnings("unchecked")
    public ClusterResult clusterEvents(List<Map<String, Object>> articles, double threshold) {
        Map<String, Object> body = Map.of(
                "articles", articles,
                "threshold", threshold
        );

        try {
            Map<?, ?> result = intelligenceRestClient.post()
                    .uri("/internal/event/cluster")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (result == null) {
                throw new IntelligenceServiceException("Python 事件聚类服务返回空响应");
            }

            int totalArticles = toInt(result.get("total_articles"));
            int clusteredArticles = toInt(result.get("clustered_articles"));
            int unclusteredArticles = toInt(result.get("unclustered_articles"));

            List<Map<String, Object>> events = (List<Map<String, Object>>) result.get("events");
            List<EventClusterItem> items = new ArrayList<>();
            if (events != null) {
                for (Map<String, Object> e : events) {
                    items.add(new EventClusterItem(
                            safeString(e.get("title"), "未命名事件"),
                            safeStringList(e.get("keywords")),
                            toLongList(e.get("article_ids")),
                            toInt(e.get("article_count")),
                            toDouble(e.get("hotness")),
                            safeString(e.get("lifecycle"), "潜伏期"),
                            safeString(e.get("start_time"), ""),
                            safeString(e.get("end_time"), "")
                    ));
                }
            }

            return new ClusterResult(items, totalArticles, clusteredArticles, unclusteredArticles);

        } catch (IntelligenceServiceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new IntelligenceServiceException("调用 Python 事件聚类服务失败", e);
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number num) return num.intValue();
        return 0;
    }

    private String safeString(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> safeStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        return List.of();
    }

    private List<Long> toLongList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Number.class::isInstance)
                    .map(o -> ((Number) o).longValue())
                    .toList();
        }
        return List.of();
    }

    public record EventClusterItem(
            String title,
            List<String> keywords,
            List<Long> articleIds,
            int articleCount,
            double hotness,
            String lifecycle,
            String startTime,
            String endTime
    ) {}

    public record ClusterResult(
            List<EventClusterItem> events,
            int totalArticles,
            int clusteredArticles,
            int unclusteredArticles
    ) {}

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> classifyTopics(List<Map<String, Object>> events) {
        Map<String, Object> body = Map.of("events", events);

        try {
            Map<?, ?> result = intelligenceRestClient.post()
                    .uri("/internal/topic/classify")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (result == null) {
                throw new IntelligenceServiceException("Python 主题分类服务返回空响应");
            }

            Object eventsObj = result.get("events");
            if (eventsObj instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(o -> (Map<String, Object>) o)
                        .toList();
            }
            return events;

        } catch (IntelligenceServiceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new IntelligenceServiceException("调用 Python 主题分类服务失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> forecastTrend(List<Map<String, Object>> dailyCounts, int periods) {
        Map<String, Object> body = Map.of(
                "daily_counts", dailyCounts,
                "periods", periods
        );

        try {
            Map<?, ?> result = intelligenceRestClient.post()
                    .uri("/internal/trend/forecast")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (result == null) {
                throw new IntelligenceServiceException("Python 趋势预测服务返回空响应");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("method", result.get("method"));
            response.put("historical", result.get("historical"));
            response.put("forecast", result.get("forecast"));
            response.put("trend", result.get("trend"));
            Object note = result.get("note");
            response.put("note", note != null ? note : "");
            return response;

        } catch (IntelligenceServiceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new IntelligenceServiceException("调用 Python 趋势预测服务失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzePropagation(Map<String, Object> payload) {
        try {
            Map<?, ?> result = intelligenceRestClient.post()
                    .uri("/internal/event/propagation")
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            if (result == null) {
                throw new IntelligenceServiceException("Python 传播分析服务返回空响应");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("spread_depth", safeInt(result.get("spread_depth"), 0));
            response.put("total_nodes", safeInt(result.get("total_nodes"), 0));
            response.put("duration_hours", toBigDecimal(result.get("duration_hours")));
            response.put("spread_speed", toBigDecimal(result.get("spread_speed")));
            response.put("method", safeString(result.get("method"), "fallback"));

            // nodes
            List<Map<String, Object>> nodes = new ArrayList<>();
            Object nodesObj = result.get("nodes");
            if (nodesObj instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, Object> node = new HashMap<>();
                        node.put("cleanId", safeInt(m.get("id"), 0));
                        node.put("articleTitle", safeString(m.get("title"), ""));
                        node.put("sourceName", safeString(m.get("source_name"), ""));
                        node.put("publishedAt", safeString(m.get("published_at"), ""));
                        node.put("depth", safeInt(m.get("depth"), 0));
                        node.put("isSource", Boolean.TRUE.equals(m.get("is_source")));
                        node.put("isInfluencer", Boolean.TRUE.equals(m.get("is_influencer")));
                        node.put("nodeType", safeString(m.get("node_type"), "commercial"));
                        nodes.add(node);
                    }
                }
            }
            response.put("nodes", nodes);

            // edges
            List<Map<String, Object>> edges = new ArrayList<>();
            Object edgesObj = result.get("edges");
            if (edgesObj instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, Object> edge = new HashMap<>();
                        edge.put("source", safeInt(m.get("source"), 0));
                        edge.put("target", safeInt(m.get("target"), 0));
                        edge.put("similarity", toBigDecimal(m.get("similarity")));
                        edges.add(edge);
                    }
                }
            }
            response.put("edges", edges);

            return response;

        } catch (IntelligenceServiceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new IntelligenceServiceException("调用 Python 传播分析服务失败", e);
        }
    }

    private int safeInt(Object value, int defaultValue) {
        if (value instanceof Number num) return num.intValue();
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getEventSummary(Map<String, Object> eventData) {
        try {
            Map<?, ?> result = intelligenceRestClient.post()
                    .uri("/internal/event/summary")
                    .body(eventData)
                    .retrieve()
                    .body(Map.class);

            if (result == null) {
                throw new IntelligenceServiceException("Python 事件摘要服务返回空响应");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("summary", safeString(result.get("summary"), ""));
            response.put("time", safeString(result.get("time"), ""));
            response.put("location", safeString(result.get("location"), ""));
            response.put("cause", safeString(result.get("cause"), ""));
            response.put("persons", safeString(result.get("persons"), ""));
            response.put("key_steps", safeString(result.get("key_steps"), ""));
            response.put("important_info", safeString(result.get("important_info"), ""));
            response.put("method", safeString(result.get("method"), "fallback"));
            return response;

        } catch (IntelligenceServiceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new IntelligenceServiceException("调用 Python 事件摘要服务失败", e);
        }
    }
}
