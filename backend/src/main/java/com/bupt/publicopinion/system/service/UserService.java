package com.bupt.publicopinion.system.service;

import com.bupt.publicopinion.system.entity.User;

public interface UserService {

    /**
     * 根据用户名查找用户。
     */
    User findByUsername(String username);

    /**
     * 根据用户ID查找用户。
     */
    User findById(Long id);

    /**
     * 创建用户（管理员预创建账号 / 预留注册扩展点）。
     */
    User createUser(User user);

    /**
     * 更新最后登录时间。
     */
    void updateLastLogin(Long userId);

    /**
     * 更新个人信息（昵称、邮箱）。
     */
    void updateProfile(Long userId, String nickname, String email);

    /**
     * 修改密码。
     */
    void changePassword(Long userId, String oldPassword, String newPassword);
}
