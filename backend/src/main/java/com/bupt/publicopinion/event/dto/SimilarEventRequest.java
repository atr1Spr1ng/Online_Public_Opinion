package com.bupt.publicopinion.event.dto;

import jakarta.validation.constraints.NotBlank;

public class SimilarEventRequest {

    @NotBlank(message = "关键词不能为空")
    private String keywords;

    private int topK = 5;

    public SimilarEventRequest() {}

    public SimilarEventRequest(String keywords, int topK) {
        this.keywords = keywords;
        this.topK = topK > 0 ? topK : 5;
    }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK > 0 ? topK : 5; }
}
