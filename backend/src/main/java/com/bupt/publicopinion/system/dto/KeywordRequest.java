package com.bupt.publicopinion.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KeywordRequest(
        @NotBlank(message = "关键词不能为空")
        @Size(max = 100, message = "关键词长度不能超过100")
        String keyword
) {}
