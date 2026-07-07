package com.bupt.publicopinion.collection.vo;

import java.time.OffsetDateTime;
import java.util.List;

public record NewsCrawlResult(
        String engine,
        String originalUrl,
        String finalUrl,
        int statusCode,
        String title,
        List<String> authors,
        String publishedAt,
        String content,
        int contentLength,
        String extractStatus,
        String message,
        String mainImage,
        String language,
        OffsetDateTime fetchedAt
) {
}
