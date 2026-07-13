package com.bupt.publicopinion.collection.vo;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDateTime;
import java.util.List;

public record SocialHotResult(
        String platform,
        List<SocialHotItem> items,
        @JsonAlias("fetched_at") LocalDateTime fetchedAt
) {}
