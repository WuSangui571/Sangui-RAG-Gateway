package com.sangui.raggateway.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business-error")
        void throwBusinessException() {
            throw new BusinessException("TEST_ERROR", "Test business error message");
        }

        @GetMapping("/test/unexpected-error")
        void throwRuntimeException() {
            throw new RuntimeException("Unexpected failure");
        }
    }

    @Test
    void shouldHandleBusinessExceptionWithApiResponse() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEST_ERROR"))
                .andExpect(jsonPath("$.message").value("Test business error message"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void shouldHideStackTraceForUnexpectedErrors() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
