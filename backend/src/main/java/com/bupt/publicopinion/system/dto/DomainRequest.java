package com.bupt.publicopinion.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DomainRequest(
        @NotBlank(message = "领域名称不能为空")
        @Size(max = 100, message = "领域名称长度不能超过100")
        String domainName
) {}
