package com.bupt.publicopinion.system.vo;

import com.bupt.publicopinion.collection.entity.NewsSource;
import com.bupt.publicopinion.system.entity.UserSourceSubscription;

public record SourceSubscriptionVO(
        Long subscriptionId,
        Long sourceId,
        String sourceName,
        String sourceType,
        String sourceUrl,
        boolean subscribed
) {
    public static SourceSubscriptionVO from(NewsSource source, UserSourceSubscription sub) {
        return new SourceSubscriptionVO(
                sub != null ? sub.getId() : null,
                source.getId(),
                source.getSourceName(),
                source.getSourceType(),
                source.getSourceUrl(),
                sub != null
        );
    }
}
