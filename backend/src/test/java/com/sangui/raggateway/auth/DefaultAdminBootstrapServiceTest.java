package com.sangui.raggateway.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.common.security.AdminJwtService;
import com.sangui.raggateway.common.security.PasswordHasher;
import com.sangui.raggateway.user.UserEntity;
import com.sangui.raggateway.user.UserMapper;
import com.sangui.raggateway.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAdminBootstrapServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private Environment environment;

    private UserService userService;
    private PasswordHasher passwordHasher;

    private static final String DEV_PROFILE = "dev";
    private static final String PROD_PROFILE = "prod";
    private static final String STRONG_PASSWORD = "a-strong-production-password-2024!";
    private static final String DEV_DEFAULT_PASSWORD = "admin123";
    private static final String WEAK_PLACEHOLDER = "local-dev-change-me";

    private DefaultAdminBootstrapService createService(String username, String password, boolean allowDefaultAdmin) {
        DefaultAdminBootstrapService service = new DefaultAdminBootstrapService(
                userMapper, userService, passwordHasher, environment);
        ReflectionTestUtils.setField(service, "defaultAdminUsername", username);
        ReflectionTestUtils.setField(service, "defaultAdminPassword", password);
        ReflectionTestUtils.setField(service, "allowDefaultAdmin", allowDefaultAdmin);
        return service;
    }

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper);
        passwordHasher = new PasswordHasher();
    }

    @Nested
    class DevProfile {

        @Test
        void shouldCreateAdminWhenTableEmpty() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{DEV_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);
            AtomicReference<UserEntity> ref = new AtomicReference<>();
            doAnswer(inv -> {
                ref.set(inv.getArgument(0));
                return 1;
            }).when(userMapper).insert(any(UserEntity.class));

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            verify(userMapper).insert(any(UserEntity.class));
            UserEntity inserted = ref.get();
            assertThat(inserted).isNotNull();
            assertThat(inserted.getUsername()).isEqualTo("admin");
            assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
            assertThat(inserted.getPasswordHash()).isNotEqualTo(STRONG_PASSWORD);
            assertThat(inserted.getPasswordHash()).startsWith("$2a$");
            assertThat(inserted.getCreatedAt()).isNotNull();
            assertThat(inserted.getUpdatedAt()).isNotNull();
        }

        @Test
        void shouldSkipWhenTableNonEmpty() {
            when(userMapper.selectCount(null)).thenReturn(1L);

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            verify(userMapper, never()).insert(any(UserEntity.class));
        }

        @Test
        void shouldFailWhenUsernameBlank() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{DEV_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("  ", STRONG_PASSWORD, false);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rag.admin-auth.default-admin-username")
                    .hasMessageContaining("must not be blank");
        }

        @Test
        void shouldFailWhenPasswordBlank() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{DEV_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", "  ", false);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rag.admin-auth.default-admin-password")
                    .hasMessageContaining("must not be blank");
        }

        @Test
        void shouldFailWhenPasswordIsWeakPlaceholder() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{DEV_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", WEAK_PLACEHOLDER, false);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rag.admin-auth.default-admin-password")
                    .hasMessageContaining("weak placeholder");
        }
    }

    @Nested
    class NoProfile {

        @Test
        void shouldCreateAdminWhenTableEmpty() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            verify(userMapper).insert(any(UserEntity.class));
        }

        @Test
        void shouldSkipWhenTableNonEmpty() {
            when(userMapper.selectCount(null)).thenReturn(1L);

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            verify(userMapper, never()).insert(any(UserEntity.class));
        }
    }

    @Nested
    class ProductionProfile {

        @Test
        void shouldSkipWhenAllowDefaultAdminFalse() {
            when(userMapper.selectCount(null)).thenReturn(0L);
            when(environment.getActiveProfiles()).thenReturn(new String[]{PROD_PROFILE});

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            verify(userMapper, never()).insert(any(UserEntity.class));
        }

        @Test
        void shouldSkipWhenAllowDefaultAdminAbsent() {
            when(userMapper.selectCount(null)).thenReturn(0L);
            when(environment.getActiveProfiles()).thenReturn(new String[]{PROD_PROFILE});

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            verify(userMapper, never()).insert(any(UserEntity.class));
        }

        @Test
        void shouldCreateAdminWhenAllowedWithStrongPassword() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{PROD_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);
            AtomicReference<UserEntity> ref = new AtomicReference<>();
            doAnswer(inv -> {
                ref.set(inv.getArgument(0));
                return 1;
            }).when(userMapper).insert(any(UserEntity.class));

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, true);
            service.run(new DefaultApplicationArguments(new String[0]));

            verify(userMapper).insert(any(UserEntity.class));
            UserEntity inserted = ref.get();
            assertThat(inserted).isNotNull();
            assertThat(inserted.getUsername()).isEqualTo("admin");
            assertThat(inserted.getPasswordHash()).isNotEqualTo(STRONG_PASSWORD);
            assertThat(passwordHasher.verify(STRONG_PASSWORD, inserted.getPasswordHash())).isTrue();
        }

        @Test
        void shouldFailWhenPasswordMissing() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{PROD_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", "", true);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rag.admin-auth.default-admin-password")
                    .hasMessageContaining("must not be blank");
        }

        @Test
        void shouldFailWhenPasswordIsWeakPlaceholder() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{PROD_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", WEAK_PLACEHOLDER, true);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rag.admin-auth.default-admin-password")
                    .hasMessageContaining("weak placeholder");
        }

        @Test
        void shouldFailWhenPasswordIsDevDefault() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{PROD_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", DEV_DEFAULT_PASSWORD, true);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rag.admin-auth.default-admin-password")
                    .hasMessageContaining("strong explicit production password");
        }

        @Test
        void shouldFailWhenPasswordIsShort() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{PROD_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", "short-pass", true);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rag.admin-auth.default-admin-password")
                    .hasMessageContaining("strong explicit production password");
        }

        @Test
        void shouldFailWhenUsernameBlank() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{PROD_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("  ", STRONG_PASSWORD, true);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("rag.admin-auth.default-admin-username")
                    .hasMessageContaining("must not be blank");
        }

        @Test
        void productionProfileWithProductionNameShouldAlsoSkip() {
            when(userMapper.selectCount(null)).thenReturn(0L);
            when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            verify(userMapper, never()).insert(any(UserEntity.class));
        }
    }

    @Nested
    class Idempotency {

        @Test
        void shouldNotInsertWhenTableNonEmptyRegardlessOfProfile() {
            when(userMapper.selectCount(null)).thenReturn(5L);

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            verify(userMapper, never()).insert(any(UserEntity.class));
        }

        @Test
        void shouldNotInsertWhenTableNonEmptyEvenInProdWithAllow() {
            when(userMapper.selectCount(null)).thenReturn(3L);

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, true);
            service.run(new DefaultApplicationArguments(new String[0]));

            verify(userMapper, never()).insert(any(UserEntity.class));
        }
    }

    @Nested
    class PasswordHashing {

        @Test
        void storedHashShouldNotEqualPlaintext() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{DEV_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);
            AtomicReference<UserEntity> ref = new AtomicReference<>();
            doAnswer(inv -> {
                ref.set(inv.getArgument(0));
                return 1;
            }).when(userMapper).insert(any(UserEntity.class));

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            assertThat(ref.get().getPasswordHash()).isNotEqualTo(STRONG_PASSWORD);
        }

        @Test
        void storedHashShouldBeBcryptPrefixed() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{DEV_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);
            AtomicReference<UserEntity> ref = new AtomicReference<>();
            doAnswer(inv -> {
                ref.set(inv.getArgument(0));
                return 1;
            }).when(userMapper).insert(any(UserEntity.class));

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            assertThat(ref.get().getPasswordHash()).startsWith("$2a$");
        }

        @Test
        void bootstrappedAdminShouldLoginThroughExistingAuthService() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{DEV_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);
            AtomicReference<UserEntity> ref = new AtomicReference<>();
            doAnswer(inv -> {
                UserEntity inserted = inv.getArgument(0);
                inserted.setId(100L);
                ref.set(inserted);
                return 1;
            }).when(userMapper).insert(any(UserEntity.class));

            DefaultAdminBootstrapService service = createService("admin", STRONG_PASSWORD, false);
            service.run(new DefaultApplicationArguments(new String[0]));

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ref.get());
            AdminAuthService authService = new AdminAuthService(
                    userService,
                    passwordHasher,
                    new AdminJwtService("test-secret-key-for-jwt-signing-min-256-bits!!", 3600));

            AdminAuthService.AdminLoginResult result = authService.login("admin", STRONG_PASSWORD);

            assertThat(result).isNotNull();
            assertThat(result.getPayload().getUserId()).isEqualTo(100L);
            assertThat(result.getPayload().getUsername()).isEqualTo("admin");
        }
    }

    @Nested
    class SecretSafety {

        @Test
        void exceptionMessageShouldNotContainPasswordValue() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{DEV_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", "  ", false);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(ex -> {
                        assertThat(ex.getMessage()).doesNotContain("  ");
                    });
        }

        @Test
        void exceptionMessageShouldNotContainWeakPlaceholderValue() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{DEV_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", WEAK_PLACEHOLDER, false);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(ex -> {
                        assertThat(ex.getMessage()).doesNotContain(WEAK_PLACEHOLDER);
                    });
        }

        @Test
        void exceptionMessageShouldNamePropertyNotValue() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{PROD_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", "", true);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(ex -> {
                        String msg = ex.getMessage();
                        assertThat(msg).contains("rag.admin-auth.default-admin-password");
                        assertThat(msg).doesNotContain(STRONG_PASSWORD);
                    });
        }

        @Test
        void productionDevDefaultFailureShouldNotContainPasswordValue() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{PROD_PROFILE});
            when(userMapper.selectCount(null)).thenReturn(0L);

            DefaultAdminBootstrapService service = createService("admin", DEV_DEFAULT_PASSWORD, true);

            assertThatThrownBy(() -> service.run(new DefaultApplicationArguments(new String[0])))
                    .isInstanceOf(IllegalStateException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(DEV_DEFAULT_PASSWORD));
        }
    }
}
