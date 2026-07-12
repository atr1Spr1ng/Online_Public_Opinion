package com.bupt.publicopinion.collection.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TopicSearchResult(
        String keyword,
        @JsonProperty("source_name") String sourceName,
        @JsonProperty("source_type") String sourceType,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("total_found") int totalFound,
        @JsonProperty("total_success") int totalSuccess,
        @JsonProperty("total_failed") int totalFailed,
        List<NewsCrawlResult> articles,
        List<FailedNewsCrawl> failures
) {
}
