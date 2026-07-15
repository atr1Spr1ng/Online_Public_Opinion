package com.bupt.publicopinion.system.service;

import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.system.entity.User;

public interface UserService {

    /**
     * 根据用户名查找用户。
     */
    User findByUsername(String username);

    /**
     * 根据用户名查找用户，不过滤启用状态。主要用于登录时区分用户不存在和账号禁用。
     */
    User findByUsernameIncludingDisabled(String username);

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

    /**
     * 编辑用户（管理员）。
     */
    void updateUser(Long userId, String nickname, String email, String role);

    /**
     * 重置用户密码（管理员）。
     */
    void resetPassword(Long userId, String newPassword);

    /**
     * 删除用户（管理员）。
     */
    void deleteUser(Long userId);
}
