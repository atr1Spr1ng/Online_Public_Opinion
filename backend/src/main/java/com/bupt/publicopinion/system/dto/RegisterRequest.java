package com.bupt.publicopinion.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度需在3-50之间")
        @Pattern(regexp = "^[\\p{L}\\p{N}_-]+$", message = "用户名只能包含中英文、数字、下划线和短横线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度需在6-100之间")
        String password,

        @Size(max = 50, message = "昵称长度不能超过50")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱长度不能超过100")
        String email
) {
    public RegisterRequest {
        username = username == null ? null : username.trim();
        nickname = normalizeOptional(nickname);
        email = normalizeOptional(email);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
