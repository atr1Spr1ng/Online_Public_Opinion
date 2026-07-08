package com.bupt.publicopinion.report.dto;

import jakarta.validation.constraints.NotBlank;

public record QaRequest(
        @NotBlank(message = "问题不能为空")
        String question,
        Long reportId
) {
}
