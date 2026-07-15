package com.bupt.publicopinion.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度需在3-50之间")
        @Pattern(regexp = "^[\\p{L}\\p{N}_-]+$", message = "用户名只能包含中英文、数字、下划线和短横线")
        String username,

        @NotBlank(message = "密码不能为空")
        String password
) {
    public LoginRequest {
        username = username == null ? null : username.trim();
    }
}
