package com.sangui.raggateway.app;

import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppServiceTest {

    @Mock
    private AppMapper appMapper;

    @Mock
    private ModelConfigService modelConfigService;

    private AppService appService;

    @BeforeEach
    void setUp() {
        appService = new AppService(appMapper, modelConfigService);
    }

    @Test
    void shouldResolveDefaultModelConfigWithAppUserBoundary() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setDefaultModelConfigId(10L);

        ModelConfigEntity modelConfig = new ModelConfigEntity();
        modelConfig.setId(10L);
        modelConfig.setUserId(100L);
        when(modelConfigService.findEnabledByIdAndUserId(10L, 100L)).thenReturn(modelConfig);

        ModelConfigEntity result = appService.resolveDefaultModelConfig(app);

        assertThat(result).isSameAs(modelConfig);
        verify(modelConfigService).findEnabledByIdAndUserId(10L, 100L);
    }

    @Test
    void shouldNotResolveWhenDefaultModelConfigIsMissing() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);

        ModelConfigEntity result = appService.resolveDefaultModelConfig(app);

        assertThat(result).isNull();
        verifyNoInteractions(modelConfigService);
    }

    @Test
    void shouldReturnNullWhenDefaultModelConfigDoesNotMatchAppUser() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setDefaultModelConfigId(10L);
        when(modelConfigService.findEnabledByIdAndUserId(10L, 100L)).thenReturn(null);

        ModelConfigEntity result = appService.resolveDefaultModelConfig(app);

        assertThat(result).isNull();
        verify(modelConfigService).findEnabledByIdAndUserId(10L, 100L);
    }
}
