package com.bupt.publicopinion.system.vo;

import com.bupt.publicopinion.system.entity.User;

public record UserProfileVO(
        Long id,
        String username,
        String nickname,
        String email,
        String role,
        String avatar
) {
    public static UserProfileVO from(User user) {
        return new UserProfileVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getRole(),
                user.getAvatar()
        );
    }
}
