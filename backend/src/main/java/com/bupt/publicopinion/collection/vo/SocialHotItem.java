package com.bupt.publicopinion.collection.vo;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SocialHotItem(
        int rank,
        String title,
        @JsonAlias("hot_score") Object hotScore,
        String url,
        String summary
) {}
