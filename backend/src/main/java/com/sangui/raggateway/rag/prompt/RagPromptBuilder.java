package com.sangui.raggateway.rag.prompt;

import com.sangui.raggateway.gateway.openai.OpenAiChatMessage;
import com.sangui.raggateway.retrieval.RetrievalResult;

import java.util.ArrayList;
import java.util.List;

public class RagPromptBuilder {

    private static final String RAG_INSTRUCTION_HITS = """
            You are answering a user question using provided private knowledge-base context.\s
            Use the context below when relevant to the question. If the context is not relevant,\s
            answer based on your general knowledge but note that you do not have specific information\s
            in the knowledge base.\s
            Do not mention the context structure or chunk IDs in your answer.""";

    private static final String RAG_INSTRUCTION_NO_HITS = """
            The knowledge base did not contain relevant information for this question.\s
            Inform the user that the knowledge base does not contain enough information\s
            to answer when the question requires private knowledge base facts.\s
            Do not fabricate information pretending to come from the knowledge base.""";

    private RagPromptBuilder() {
    }

    public static List<OpenAiChatMessage> buildAugmentedMessages(
            List<OpenAiChatMessage> originalMessages,
            RetrievalResult retrievalResult) {

        List<OpenAiChatMessage> augmented = new ArrayList<>();

        String contextMessage;
        if (retrievalResult.isNoHits()) {
            contextMessage = buildNoHitContext();
        } else {
            contextMessage = buildHitContext(retrievalResult);
        }

        int lastSystemIndex = -1;
        for (int i = 0; i < originalMessages.size(); i++) {
            if ("system".equals(originalMessages.get(i).getRole())) {
                lastSystemIndex = i;
            }
        }

        for (int i = 0; i < originalMessages.size(); i++) {
            augmented.add(new OpenAiChatMessage(
                    originalMessages.get(i).getRole(),
                    originalMessages.get(i).getContent()));
            if (i == lastSystemIndex) {
                augmented.add(new OpenAiChatMessage("system", contextMessage));
            }
        }

        if (lastSystemIndex == -1) {
            augmented.add(0, new OpenAiChatMessage("system", contextMessage));
        }

        return augmented;
    }

    static String buildHitContext(RetrievalResult retrievalResult) {
        StringBuilder sb = new StringBuilder(RAG_INSTRUCTION_HITS);
        sb.append("\n\n---PRIVATE KNOWLEDGE BASE CONTEXT---\n");
        for (int i = 0; i < retrievalResult.getChunks().size(); i++) {
            RetrievalResult.RetrievedChunk chunk = retrievalResult.getChunks().get(i);
            sb.append("[Chunk ").append(chunk.getChunkId()).append("]\n");
            sb.append(chunk.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    static String buildNoHitContext() {
        return RAG_INSTRUCTION_NO_HITS;
    }
}
