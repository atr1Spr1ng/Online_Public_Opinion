package com.bupt.publicopinion.event.dto;

import jakarta.validation.constraints.NotBlank;

public record SimilarEventRequest(
        @NotBlank(message = "关键词不能为空")
        String keywords,

        int topK
) {
    public SimilarEventRequest {
        if (topK <= 0) {
            topK = 5;
        }
    }

    public SimilarEventRequest(String keywords) {
        this(keywords, 5);
    }
}
