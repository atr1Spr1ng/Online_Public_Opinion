package com.bupt.publicopinion.collection.vo;

import java.util.List;

public record NewsDiscoverResult(
        String sourceUrl,
        String finalUrl,
        int statusCode,
        String sourceName,
        String sourceType,
        int totalFound,
        List<DiscoveredNewsLink> links
) {
}
