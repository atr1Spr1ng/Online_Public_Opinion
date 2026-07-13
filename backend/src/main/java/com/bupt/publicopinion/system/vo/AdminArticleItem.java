package com.bupt.publicopinion.system.vo;

import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.content.entity.ArticleClean;

import java.time.LocalDateTime;

public record AdminArticleItem(Long id, String title, String sourceName, LocalDateTime createTime) {
    public static AdminArticleItem fromRaw(ArticleRaw a) {
        return new AdminArticleItem(a.getId(), a.getTitle(), a.getSourceName(), a.getCreateTime());
    }

    public static AdminArticleItem fromClean(ArticleClean a) {
        return new AdminArticleItem(a.getId(), a.getTitle(), a.getSourceName(), a.getCreateTime());
    }
}
