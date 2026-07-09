package com.bupt.publicopinion.event.vo;

import com.bupt.publicopinion.event.entity.Event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EventDetailVO(
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
        List<Long> articleIds
) {
    public static EventDetailVO from(Event event, List<Long> articleIds) {
        return new EventDetailVO(
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
                articleIds
        );
    }
}
