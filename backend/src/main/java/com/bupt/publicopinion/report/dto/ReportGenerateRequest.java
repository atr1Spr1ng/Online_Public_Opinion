package com.bupt.publicopinion.report.dto;

import jakarta.validation.constraints.NotNull;

public record ReportGenerateRequest(
        @NotNull(message = "eventId 不能为空")
        Long eventId
) {
}
