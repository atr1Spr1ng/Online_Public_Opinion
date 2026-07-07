package com.bupt.publicopinion.analysis.dto;

import jakarta.validation.constraints.NotNull;

public record SentimentRequest(
        @NotNull(message = "cleanId 不能为空")
        Long cleanId
) {
}
