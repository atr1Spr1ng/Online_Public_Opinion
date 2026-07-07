package com.bupt.publicopinion.collection.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NewsDiscoverRequest(
        @NotBlank(message = "url 不能为空")
        @Pattern(regexp = "^https?://.+", message = "url 必须使用 http 或 https")
        String url,

        @Min(value = 1, message = "limit 最小为 1")
        @Max(value = 100, message = "limit 最大为 100")
        Integer limit
) {
    public NewsDiscoverRequest {
        if (limit == null) {
            limit = 5;
        }
    }
}
