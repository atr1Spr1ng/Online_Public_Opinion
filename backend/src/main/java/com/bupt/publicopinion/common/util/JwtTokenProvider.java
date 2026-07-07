package com.bupt.publicopinion.common.util;

import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.config.SystemProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SystemProperties systemProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(SystemProperties systemProperties) {
        this.systemProperties = systemProperties;
        byte[] keyBytes = Base64.getDecoder().decode(systemProperties.secret());
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserContext.UserContextInfo user) {
        return generateToken(user, systemProperties.accessTokenExpiration());
    }

    public String generateRefreshToken(UserContext.UserContextInfo user) {
        return generateToken(user, systemProperties.refreshTokenExpiration());
    }

    private String generateToken(UserContext.UserContextInfo user, Duration expiration) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.userId().toString())
                .claim("username", user.username())
                .claim("role", user.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(secretKey)
                .compact();
    }

    public UserContext.UserContextInfo parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        return new UserContext.UserContextInfo(
                Long.parseLong(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class)
        );
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new com.bupt.publicopinion.common.exception.AuthenticationException("token 已过期");
        } catch (JwtException e) {
            throw new com.bupt.publicopinion.common.exception.AuthenticationException("token 无效");
        }
    }

    /**
     * 校验 refresh token 是否有效（未过期、签名正确）。
     * 不关心过期时间的 token 类型不会到达这里，此方法仅用于 refresh 场景。
     */
    public UserContext.UserContextInfo parseRefreshToken(String token) {
        return parseAccessToken(token); // 解析逻辑相同
    }
}
