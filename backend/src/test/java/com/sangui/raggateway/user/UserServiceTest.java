package com.sangui.raggateway.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper);
    }

    @Test
    void shouldFindByUsername() {
        UserEntity user = new UserEntity();
        user.setId(100L);
        user.setUsername("admin");
        user.setPasswordHash("hash");
        user.setStatus("ACTIVE");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        UserEntity result = userService.findByUsername("admin");
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getUsername()).isEqualTo("admin");
    }

    @Test
    void shouldReturnNullForUnknownUsername() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        UserEntity result = userService.findByUsername("unknown");
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullForBlankUsername() {
        assertThat(userService.findByUsername(null)).isNull();
        assertThat(userService.findByUsername("  ")).isNull();
    }

    @Test
    void shouldFindById() {
        UserEntity user = new UserEntity();
        user.setId(100L);
        user.setUsername("admin");
        when(userMapper.selectById(100L)).thenReturn(user);

        UserEntity result = userService.findById(100L);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    void shouldReturnNullForUnknownId() {
        when(userMapper.selectById(999L)).thenReturn(null);
        assertThat(userService.findById(999L)).isNull();
    }

    @Test
    void shouldReturnNullForNullId() {
        assertThat(userService.findById(null)).isNull();
    }

    @Test
    void shouldCheckActiveStatus() {
        UserEntity activeUser = new UserEntity();
        activeUser.setStatus("ACTIVE");
        assertThat(userService.isActive(activeUser)).isTrue();

        UserEntity disabledUser = new UserEntity();
        disabledUser.setStatus("DISABLED");
        assertThat(userService.isActive(disabledUser)).isFalse();

        assertThat(userService.isActive(null)).isFalse();
    }

    @Test
    void shouldCountUsers() {
        when(userMapper.selectCount(null)).thenReturn(2L);

        assertThat(userService.countUsers()).isEqualTo(2L);
    }
}
