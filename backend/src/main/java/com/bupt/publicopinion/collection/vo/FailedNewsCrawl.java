package com.bupt.publicopinion.collection.vo;

public record FailedNewsCrawl(
        String url,
        String title,
        String reason
) {
}
