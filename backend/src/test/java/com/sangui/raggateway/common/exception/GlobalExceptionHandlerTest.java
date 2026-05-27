package com.sangui.raggateway.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        @GetMapping("/v1/models")
        void throwNoResourceForV1Models() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/v1/models");
        }

        @PostMapping("/v1/chat/completions")
        void throwNoResourceForV1ChatCompletions() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.POST, "/v1/chat/completions");
        }

        @GetMapping("/favicon.ico")
        void throwNoResourceForFavicon() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/favicon.ico");
        }

        @GetMapping("/test/gateway-error")
        void throwGatewayException() {
            throw new GatewayException(
                    "Invalid request for gateway test",
                    "invalid_request_error",
                    "invalid_request",
                    HttpStatus.BAD_REQUEST
            );
        }

        @GetMapping("/test/gateway-invalid-api-key")
        void throwGatewayInvalidApiKey() {
            throw new GatewayException(
                    "Invalid API key",
                    "invalid_request_error",
                    "invalid_api_key",
                    HttpStatus.UNAUTHORIZED
            );
        }
    }

    @Test
    void shouldReturnOpenAiCompatibleShapeForGatewayException() throws Exception {
        mockMvc.perform(get("/test/gateway-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Invalid request for gateway test"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java."))));
    }

    @Test
    void shouldReturn401ForGatewayInvalidApiKey() throws Exception {
        mockMvc.perform(get("/test/gateway-invalid-api-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("Invalid API key"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java."))));
    }

    @Test
    void shouldHandleBusinessExceptionWithApiResponse() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEST_ERROR"))
                .andExpect(jsonPath("$.message").value("Test business error message"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldHideStackTraceForUnexpectedErrors() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldReturn404ForV1Models() throws Exception {
        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java."))))
                .andExpect(content().string(not(containsString("\"data\":["))));
    }

    @Test
    void shouldReturn404ForV1ChatCompletions() throws Exception {
        mockMvc.perform(post("/v1/chat/completions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java."))));
    }

    @Test
    void shouldReturn404ForFavicon() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java."))));
    }

    @Test
    void shouldReturn404ForUnmappedUnknownRoute() throws Exception {
        mockMvc.perform(get("/this-route-does-not-exist-anywhere"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java."))));
    }
}
