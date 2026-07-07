package com.bupt.publicopinion.collection.vo;

public record BatchCrawlerTaskFailure(
        Long sourceId,
        String sourceName,
        String sourceUrl,
        String reason
) {
}
