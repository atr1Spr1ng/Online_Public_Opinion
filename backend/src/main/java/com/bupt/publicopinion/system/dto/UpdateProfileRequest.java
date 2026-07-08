package com.bupt.publicopinion.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 50, message = "昵称长度不能超过50")
        String nickname,

        @Email(message = "邮箱格式不正确")
        String email
) {}
