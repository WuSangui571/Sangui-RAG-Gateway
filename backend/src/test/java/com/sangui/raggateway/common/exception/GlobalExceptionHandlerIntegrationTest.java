package com.sangui.raggateway.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnSafe404ForUnknownRoute() throws Exception {
        assertSafe404(get("/this-route-does-not-exist-anywhere"));
    }

    @Test
    void shouldReturnSafe404ForFavicon() throws Exception {
        assertSafe404(get("/favicon.ico"));
    }

    @Test
    void shouldReturnSafe404ForUnimplementedModelsRoute() throws Exception {
        assertSafe404(get("/v1/models"))
                .andExpect(content().string(not(containsString("\"data\":["))));
    }

    @Test
    void shouldReturnSafe404ForUnimplementedChatCompletionsRoute() throws Exception {
        assertSafe404(post("/v1/chat/completions"))
                .andExpect(content().string(not(containsString("chat.completion"))));
    }

    private ResultActions assertSafe404(RequestBuilder requestBuilder) throws Exception {
        return mockMvc.perform(requestBuilder)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java."))));
    }
}
