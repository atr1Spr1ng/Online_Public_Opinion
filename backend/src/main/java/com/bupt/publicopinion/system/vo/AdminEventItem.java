package com.bupt.publicopinion.system.vo;

import com.bupt.publicopinion.event.entity.Event;

import java.math.BigDecimal;

public record AdminEventItem(Long id, String title, String category, BigDecimal hotness, Integer articleCount) {
    public static AdminEventItem from(Event e) {
        return new AdminEventItem(e.getId(), e.getTitle(), e.getCategory(), e.getHotness(), e.getArticleCount());
    }
}
