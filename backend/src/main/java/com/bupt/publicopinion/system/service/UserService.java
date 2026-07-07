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
     * 扩展点：当前仅预留接口，AuthController 中暂不调用。
     */
    void updateLastLogin(Long userId);
}
