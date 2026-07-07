package com.bupt.publicopinion.collection.vo;

import com.bupt.publicopinion.collection.entity.CrawlTask;
import com.bupt.publicopinion.collection.entity.CrawlTaskItem;

import java.util.List;

public record CrawlTaskDetailResult(
        CrawlTask task,
        List<CrawlTaskItem> items
) {
}
