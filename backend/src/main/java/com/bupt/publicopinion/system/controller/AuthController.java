package com.bupt.publicopinion.system.controller;

import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.exception.AuthenticationException;
import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.common.util.JwtTokenProvider;
import com.bupt.publicopinion.config.SystemProperties;
import com.bupt.publicopinion.system.dto.LoginRequest;
import com.bupt.publicopinion.system.dto.RefreshTokenRequest;
import com.bupt.publicopinion.system.dto.RegisterRequest;
import com.bupt.publicopinion.system.entity.User;
import com.bupt.publicopinion.system.service.UserService;
import com.bupt.publicopinion.system.vo.LoginResult;
import com.bupt.publicopinion.system.vo.UserInfo;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SystemProperties systemProperties;

    public AuthController(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            SystemProperties systemProperties
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.systemProperties = systemProperties;
    }

    @PostMapping("/login")
    public ApiResult<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        User user = userService.findByUsername(request.username());

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthenticationException("用户名或密码错误");
        }

        UserContext.UserContextInfo context = new UserContext.UserContextInfo(
                user.getId(), user.getUsername(), user.getRole()
        );

        String accessToken = jwtTokenProvider.generateAccessToken(context);
        String refreshToken = jwtTokenProvider.generateRefreshToken(context);
        UserInfo userInfo = UserInfo.from(user);

        long expiresIn = systemProperties.accessTokenExpiration().toSeconds();
        return ApiResult.success(LoginResult.of(accessToken, refreshToken, expiresIn, userInfo));
    }

    @PostMapping("/refresh")
    public ApiResult<LoginResult> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        UserContext.UserContextInfo context = jwtTokenProvider.parseRefreshToken(request.refreshToken());

        // 校验用户是否仍然存在且状态正常，同时获取完整用户信息
        User user = userService.findById(context.userId());

        String accessToken = jwtTokenProvider.generateAccessToken(context);
        String refreshToken = jwtTokenProvider.generateRefreshToken(context);
        long expiresIn = systemProperties.accessTokenExpiration().toSeconds();

        return ApiResult.success(LoginResult.of(accessToken, refreshToken, expiresIn, UserInfo.from(user)));
    }

    @GetMapping("/me")
    public ApiResult<UserInfo> me() {
        UserContext.UserContextInfo context = UserContext.get();
        User user = userService.findById(context.userId());
        return ApiResult.success(UserInfo.from(user));
    }

    @PostMapping("/register")
    public ApiResult<UserInfo> register(@Valid @RequestBody RegisterRequest request) {
        if (userService.existsByUsername(request.username())) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPassword(request.password());
        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setRole("USER");

        User saved = userService.createUser(user);
        return ApiResult.success(UserInfo.from(saved));
    }
}
