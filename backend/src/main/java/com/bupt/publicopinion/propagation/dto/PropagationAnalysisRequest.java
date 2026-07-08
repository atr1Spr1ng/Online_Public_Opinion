package com.bupt.publicopinion.propagation.dto;

import jakarta.validation.constraints.NotNull;

public record PropagationAnalysisRequest(
        @NotNull(message = "eventId 不能为空")
        Long eventId
) {
}
