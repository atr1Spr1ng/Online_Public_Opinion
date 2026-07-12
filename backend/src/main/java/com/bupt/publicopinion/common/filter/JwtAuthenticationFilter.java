package com.bupt.publicopinion.common.filter;

import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.exception.AuthenticationException;
import com.bupt.publicopinion.common.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 白名单路径，不进行 JWT 校验。
     * 扩展点：后续如有其他无需认证的接口，在此追加。
     */
    private static final Set<String> WHITELIST_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/register"
    );

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (WHITELIST_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                sendUnauthorized(response, "缺少认证信息");
                return;
            }

            String token = authHeader.substring(BEARER_PREFIX.length());
            UserContext.UserContextInfo userInfo = jwtTokenProvider.parseAccessToken(token);
            UserContext.set(userInfo);
            filterChain.doFilter(request, response);
        } catch (AuthenticationException e) {
            sendUnauthorized(response, e.getMessage());
        } finally {
            UserContext.clear();
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                String.format("{\"code\":401,\"message\":\"%s\",\"data\":null}",
                        message.replace("\\", "\\\\").replace("\"", "\\\""))
        );
    }
}
