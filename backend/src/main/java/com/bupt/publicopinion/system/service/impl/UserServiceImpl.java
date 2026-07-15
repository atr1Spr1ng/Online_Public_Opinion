package com.bupt.publicopinion.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.common.exception.AuthenticationException;
import com.bupt.publicopinion.common.exception.UserNotFoundException;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.system.entity.User;
import com.bupt.publicopinion.system.mapper.UserMapper;
import com.bupt.publicopinion.system.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User findByUsername(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, normalizeUsername(username))
                        .eq(User::getStatus, 1)
        );
        if (user == null) {
            throw new UserNotFoundException("用户不存在或已被禁用: " + username);
        }
        return user;
    }

    @Override
    public User findByUsernameIncludingDisabled(String username) {
        String normalizedUsername = normalizeUsername(username);
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, normalizedUsername)
        );
        if (user == null) {
            throw new UserNotFoundException("用户不存在");
        }
        return user;
    }

    @Override
    public User findById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new UserNotFoundException("用户不存在: " + id);
        }
        return user;
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, normalizeUsername(username))
        ) > 0;
    }

    @Override
    public User createUser(User user) {
        user.setUsername(normalizeUsername(user.getUsername()));
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("用户名已存在");
        }
        return user;
    }

    @Override
    public void updateLastLogin(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void updateProfile(Long userId, String nickname, String email) {
        User user = new User();
        user.setId(userId);
        user.setNickname(nickname);
        user.setEmail(email);
        userMapper.updateById(user);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = findById(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new AuthenticationException("原密码错误");
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
    }

    @Override
    public PageResult<User> listUsers(int pageNum, int pageSize) {
        Page<User> page = new Page<>(pageNum, pageSize);
        IPage<User> result = userMapper.selectPage(page,
                new LambdaQueryWrapper<User>()
                        .orderByDesc(User::getCreatedAt));
        return new PageResult<>(result.getRecords(), result.getTotal(), pageNum, pageSize);
    }

    @Override
    public void updateUserStatus(Long userId, Integer status) {
        User user = new User();
        user.setId(userId);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void updateUser(Long userId, String nickname, String email, String role) {
        User user = new User();
        user.setId(userId);
        user.setNickname(nickname);
        user.setEmail(email);
        user.setRole(role);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long userId, String newPassword) {
        User user = new User();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    @Override
    public void deleteUser(Long userId) {
        userMapper.deleteById(userId);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }
}
