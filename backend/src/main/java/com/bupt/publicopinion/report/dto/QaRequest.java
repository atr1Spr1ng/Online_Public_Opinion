package com.bupt.publicopinion.report.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record QaRequest(
        @NotBlank(message = "问题不能为空")
        String question,
        Long reportId,
        Map<String, Object> report
) {
}
