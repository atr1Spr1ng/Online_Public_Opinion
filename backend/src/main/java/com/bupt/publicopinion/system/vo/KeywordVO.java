package com.bupt.publicopinion.system.vo;

import com.bupt.publicopinion.system.entity.UserKeyword;

public record KeywordVO(Long id, String keyword) {
    public static KeywordVO from(UserKeyword uk) {
        return new KeywordVO(uk.getId(), uk.getKeyword());
    }
}
