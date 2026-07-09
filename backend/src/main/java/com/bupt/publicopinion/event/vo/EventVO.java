package com.bupt.publicopinion.event.vo;

import com.bupt.publicopinion.event.entity.Event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventVO(
        Long id,
        String title,
        String keywords,
        Integer articleCount,
        BigDecimal hotness,
        String lifecycle,
        String category,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createTime,
        BigDecimal sentimentPositive,
        BigDecimal sentimentNegative,
        BigDecimal sentimentNeutral
) {
    public static EventVO from(Event event) {
        return new EventVO(
                event.getId(),
                event.getTitle(),
                event.getKeywords(),
                event.getArticleCount(),
                event.getHotness(),
                event.getLifecycle(),
                event.getCategory(),
                event.getStartTime(),
                event.getEndTime(),
                event.getCreateTime(),
                null, null, null
        );
    }

    public static EventVO from(Event event, BigDecimal sentimentPositive, BigDecimal sentimentNegative, BigDecimal sentimentNeutral) {
        return new EventVO(
                event.getId(),
                event.getTitle(),
                event.getKeywords(),
                event.getArticleCount(),
                event.getHotness(),
                event.getLifecycle(),
                event.getCategory(),
                event.getStartTime(),
                event.getEndTime(),
                event.getCreateTime(),
                sentimentPositive, sentimentNegative, sentimentNeutral
        );
    }
}
