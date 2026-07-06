package com.bupt.publicopinion.collection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NewsCrawlRequest(
        @NotBlank(message = "url 不能为空")
        @Pattern(regexp = "^https?://.+", message = "url 必须使用 http 或 https")
        String url
) {
}
