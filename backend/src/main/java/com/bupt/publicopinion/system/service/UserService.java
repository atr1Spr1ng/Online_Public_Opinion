package com.bupt.publicopinion.system.service;

import com.bupt.publicopinion.common.vo.PageResult;
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
     * 检查用户名是否已存在。
     */
    boolean existsByUsername(String username);

    /**
     * 创建用户。
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

    /**
     * 分页查询用户列表（管理员）。
     */
    PageResult<User> listUsers(int pageNum, int pageSize);

    /**
     * 更新用户启用/禁用状态（管理员）。
     */
    void updateUserStatus(Long userId, Integer status);
}
