package com.sangui.raggateway.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserEntity findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, username.trim());
        return userMapper.selectOne(wrapper);
    }

    public UserEntity findById(Long id) {
        if (id == null) {
            return null;
        }
        return userMapper.selectById(id);
    }

    public boolean isActive(UserEntity user) {
        return user != null && UserStatus.ACTIVE.name().equals(user.getStatus());
    }

    public long countUsers() {
        return userMapper.selectCount(null);
    }
}
