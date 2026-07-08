package com.bupt.publicopinion.collection.vo;

public record CrawlerTaskSaveResult(
        Long taskId,
        Long sourceId,
        String sourceName,
        String sourceType,
        String sourceUrl,
        int totalDiscovered,
        int totalSuccess,
        int totalDuplicate,
        int totalFailed,
        String status
) {
}
