package com.bupt.publicopinion.collection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class TopicSearchRequest {
    @NotBlank
    @Size(max = 100)
    private String keyword;

    @NotEmpty
    private List<Long> sourceIds;

    private int limit = 10;

    public TopicSearchRequest() {}

    public TopicSearchRequest(String keyword, List<Long> sourceIds, int limit) {
        this.keyword = keyword;
        this.sourceIds = sourceIds;
        this.limit = limit > 0 && limit <= 500 ? limit : 10;
    }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public List<Long> getSourceIds() { return sourceIds; }
    public void setSourceIds(List<Long> sourceIds) { this.sourceIds = sourceIds; }
    public int getLimit() { return limit > 0 && limit <= 500 ? limit : 10; }
    public void setLimit(int limit) { this.limit = limit; }
}
