package com.bupt.publicopinion.report.client;

import com.bupt.publicopinion.report.exception.ReportServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class PythonReportClient {

    private final RestClient reportRestClient;

    public PythonReportClient(RestClient reportRestClient) {
        this.reportRestClient = reportRestClient;
    }

    @SuppressWarnings("unchecked")
    public QaResult ask(Map<String, Object> body) {
        try {
            Map<String, Object> result = reportRestClient.post()
                    .uri("/internal/report/qa")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (result == null) {
                throw new ReportServiceException("Python QA 服务返回空响应");
            }

            String answer = (String) result.get("answer");
            String source = (String) result.getOrDefault("source", "keyword");
            return new QaResult(answer, source);

        } catch (ReportServiceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ReportServiceException("调用 Python QA 服务失败", e);
        }
    }

    public record QaResult(String answer, String source) {}
}
