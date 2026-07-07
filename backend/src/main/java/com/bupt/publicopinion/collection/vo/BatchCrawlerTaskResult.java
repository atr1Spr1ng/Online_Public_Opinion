package com.bupt.publicopinion.collection.vo;

import java.util.List;

public record BatchCrawlerTaskResult(
        int totalSources,
        int totalSuccess,
        int totalFailed,
        List<CrawlerTaskSaveResult> tasks,
        List<BatchCrawlerTaskFailure> failures
) {
}
