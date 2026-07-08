package com.bupt.publicopinion.system.vo;

import com.bupt.publicopinion.system.entity.User;

public record UserInfo(
        Long id,
        String username,
        String nickname,
        String email,
        String role
) {

    public static UserInfo from(User user) {
        return new UserInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getRole()
        );
    }
}
