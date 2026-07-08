package com.bupt.publicopinion.collection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NewsSourceRequest(
        @NotBlank(message = "sourceName 不能为空")
        String sourceName,

        @NotBlank(message = "sourceType 不能为空")
        String sourceType,

        @NotBlank(message = "sourceUrl 不能为空")
        @Pattern(regexp = "^https?://.+", message = "sourceUrl 必须使用 http 或 https")
        String sourceUrl,

        Integer status
) {
    public NewsSourceRequest {
        if (status == null) {
            status = 1;
        }
    }
}
