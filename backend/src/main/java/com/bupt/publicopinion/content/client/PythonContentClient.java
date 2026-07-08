package com.bupt.publicopinion.content.client;

import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.content.exception.ContentServiceException;
import com.bupt.publicopinion.content.vo.CleanResult;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class PythonContentClient {

    private final RestClient contentRestClient;

    public PythonContentClient(RestClient contentRestClient) {
        this.contentRestClient = contentRestClient;
    }

    public CleanResult clean(ArticleRaw raw) {
        Map<String, Object> body = Map.of(
                "title", raw.getTitle() != null ? raw.getTitle() : "",
                "content", raw.getContent() != null ? raw.getContent() : "",
                "url", raw.getOriginalUrl() != null ? raw.getOriginalUrl() : "",
                "language", raw.getLanguage() != null ? raw.getLanguage() : "zh"
        );

        try {
            CleanResult result = contentRestClient.post()
                    .uri("/internal/content/clean")
                    .body(body)
                    .retrieve()
                    .body(CleanResult.class);

            if (result == null) {
                throw new ContentServiceException("Python 清洗服务返回空响应");
            }
            return result;
        } catch (ContentServiceException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ContentServiceException("调用 Python 清洗服务失败", e);
        }
    }
}
