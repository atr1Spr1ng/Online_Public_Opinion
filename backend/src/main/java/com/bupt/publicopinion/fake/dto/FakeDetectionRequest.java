package com.bupt.publicopinion.fake.dto;

import jakarta.validation.constraints.NotNull;

public record FakeDetectionRequest(
        @NotNull(message = "cleanId 不能为空")
        Long cleanId
) {
}
