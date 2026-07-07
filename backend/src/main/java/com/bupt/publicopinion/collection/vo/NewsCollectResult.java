package com.bupt.publicopinion.collection.vo;

import java.util.List;

public record NewsCollectResult(
        String sourceUrl,
        String finalUrl,
        String sourceName,
        String sourceType,
        int totalDiscovered,
        int totalSuccess,
        int totalFailed,
        List<NewsCrawlResult> articles,
        List<FailedNewsCrawl> failures
) {
}
