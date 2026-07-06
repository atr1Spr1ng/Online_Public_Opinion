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
        OffsetDateTime publishedAt,
        String content,
        String mainImage,
        String language,
        OffsetDateTime fetchedAt
) {
}
