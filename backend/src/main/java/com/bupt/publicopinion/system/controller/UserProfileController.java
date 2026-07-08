package com.bupt.publicopinion.system.controller;

import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.system.dto.UpdateProfileRequest;
import com.bupt.publicopinion.system.entity.User;
import com.bupt.publicopinion.system.service.UserService;
import com.bupt.publicopinion.system.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserProfileController {

    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ApiResult<UserProfileVO> getProfile() {
        UserContext.UserContextInfo ctx = UserContext.get();
        User user = userService.findById(ctx.userId());
        return ApiResult.success(UserProfileVO.from(user));
    }

    @PutMapping("/profile")
    public ApiResult<UserProfileVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserContext.UserContextInfo ctx = UserContext.get();
        userService.updateProfile(ctx.userId(), request.nickname(), request.email());
        User user = userService.findById(ctx.userId());
        return ApiResult.success(UserProfileVO.from(user));
    }

    // ── 扩展点：密码修改 ──
    // 预留 PUT /api/user/password
    // @PutMapping("/password")
    // public ApiResult<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    //     UserContext.UserContextInfo ctx = UserContext.get();
    //     userService.changePassword(ctx.userId(), request.oldPassword(), request.newPassword());
    //     return ApiResult.success(null);
    // }
}
