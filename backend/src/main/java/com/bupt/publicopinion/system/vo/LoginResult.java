package com.bupt.publicopinion.system.vo;

public record LoginResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {

    public static LoginResult of(String accessToken, String refreshToken, long expiresIn, UserInfo user) {
        return new LoginResult(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
