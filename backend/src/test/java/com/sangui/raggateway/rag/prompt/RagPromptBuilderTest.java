package com.sangui.raggateway.rag.prompt;

import com.sangui.raggateway.gateway.openai.OpenAiChatMessage;
import com.sangui.raggateway.retrieval.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptBuilderTest {

    @Test
    void shouldPreserveOriginalMessages() {
        List<OpenAiChatMessage> original = List.of(
                new OpenAiChatMessage("system", "You are helpful."),
                new OpenAiChatMessage("user", "What is RAG?")
        );
        RetrievalResult result = createHitResult();

        List<OpenAiChatMessage> augmented = RagPromptBuilder.buildAugmentedMessages(original, result);

        assertThat(augmented).hasSize(3);
        assertThat(augmented.get(0).getRole()).isEqualTo("system");
        assertThat(augmented.get(0).getContent()).isEqualTo("You are helpful.");
        assertThat(augmented.getLast().getRole()).isEqualTo("user");
        assertThat(augmented.getLast().getContent()).isEqualTo("What is RAG?");
    }

    @Test
    void shouldInsertRagContextAfterSystemMessages() {
        List<OpenAiChatMessage> original = List.of(
                new OpenAiChatMessage("system", "You are helpful."),
                new OpenAiChatMessage("user", "Hi")
        );
        RetrievalResult result = createHitResult();

        List<OpenAiChatMessage> augmented = RagPromptBuilder.buildAugmentedMessages(original, result);

        assertThat(augmented).hasSize(3);
        assertThat(augmented.get(0).getRole()).isEqualTo("system");
        assertThat(augmented.get(0).getContent()).isEqualTo("You are helpful.");
        assertThat(augmented.get(1).getRole()).isEqualTo("system");
        assertThat(result.isNoHits()).isFalse();
        assertThat(augmented.get(1).getContent()).contains("PRIVATE KNOWLEDGE BASE CONTEXT");
    }

    @Test
    void shouldInsertRagContextAtStartWhenNoSystemMessage() {
        List<OpenAiChatMessage> original = List.of(
                new OpenAiChatMessage("user", "Hi")
        );
        RetrievalResult result = createHitResult();

        List<OpenAiChatMessage> augmented = RagPromptBuilder.buildAugmentedMessages(original, result);

        assertThat(augmented).hasSize(2);
        assertThat(augmented.get(0).getRole()).isEqualTo("system");
        assertThat(augmented.get(0).getContent()).contains("PRIVATE KNOWLEDGE BASE CONTEXT");
        assertThat(augmented.get(1).getRole()).isEqualTo("user");
    }

    @Test
    void shouldNotMutateUserMessageContent() {
        List<OpenAiChatMessage> original = List.of(
                new OpenAiChatMessage("user", "Original user question")
        );
        RetrievalResult result = createHitResult();

        List<OpenAiChatMessage> augmented = RagPromptBuilder.buildAugmentedMessages(original, result);

        assertThat(augmented.get(1).getRole()).isEqualTo("user");
        assertThat(augmented.get(1).getContent()).isEqualTo("Original user question");
    }

    @Test
    void shouldBuildNoHitStrictRagContext() {
        List<OpenAiChatMessage> original = List.of(
                new OpenAiChatMessage("user", "Unknown topic")
        );
        RetrievalResult noHitResult = new RetrievalResult(List.of(), List.of(), true, 50L);

        List<OpenAiChatMessage> augmented = RagPromptBuilder.buildAugmentedMessages(original, noHitResult);

        assertThat(augmented).hasSize(2);
        assertThat(augmented.get(0).getRole()).isEqualTo("system");
        assertThat(augmented.get(0).getContent()).contains("knowledge base does not contain enough information");
        assertThat(augmented.get(0).getContent()).doesNotContain("PRIVATE KNOWLEDGE BASE CONTEXT");
    }

    @Test
    void shouldIncludeChunkContentInHitContext() {
        RetrievalResult.RetrievedChunk chunk = new RetrievalResult.RetrievedChunk(
                42L, 10L, "This is a relevant chunk.", null, 0.85);
        RetrievalResult result = new RetrievalResult(List.of(chunk), List.of(42L), false, 50L);

        String context = RagPromptBuilder.buildHitContext(result);

        assertThat(context).contains("This is a relevant chunk.");
        assertThat(context).contains("[Chunk 42]");
        assertThat(context).contains("PRIVATE KNOWLEDGE BASE CONTEXT");
    }

    @Test
    void shouldPreserveMultipleOriginalMessagesInOrder() {
        List<OpenAiChatMessage> original = List.of(
                new OpenAiChatMessage("system", "Sys prompt"),
                new OpenAiChatMessage("user", "Q1"),
                new OpenAiChatMessage("assistant", "A1"),
                new OpenAiChatMessage("user", "Q2")
        );
        RetrievalResult result = createHitResult();

        List<OpenAiChatMessage> augmented = RagPromptBuilder.buildAugmentedMessages(original, result);

        assertThat(augmented).hasSize(5);
        assertThat(augmented.get(0).getRole()).isEqualTo("system");
        assertThat(augmented.get(0).getContent()).isEqualTo("Sys prompt");
        assertThat(augmented.get(1).getRole()).isEqualTo("system");
        assertThat(augmented.get(2).getRole()).isEqualTo("user");
        assertThat(augmented.get(2).getContent()).isEqualTo("Q1");
        assertThat(augmented.get(3).getRole()).isEqualTo("assistant");
        assertThat(augmented.get(3).getContent()).isEqualTo("A1");
        assertThat(augmented.get(4).getRole()).isEqualTo("user");
        assertThat(augmented.get(4).getContent()).isEqualTo("Q2");
    }

    private RetrievalResult createHitResult() {
        RetrievalResult.RetrievedChunk chunk = new RetrievalResult.RetrievedChunk(
                1L, 10L, "chunk content", null, 0.85);
        return new RetrievalResult(List.of(chunk), List.of(1L), false, 50L);
    }
}
