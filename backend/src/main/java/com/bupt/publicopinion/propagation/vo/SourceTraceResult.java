package com.bupt.publicopinion.propagation.vo;

public record SourceTraceResult(
        Long eventId,
        String eventTitle,
        Long sourceArticleId,
        String sourceArticleTitle,
        String sourceName,
        String publishedAt,
        String summary
) {
}
