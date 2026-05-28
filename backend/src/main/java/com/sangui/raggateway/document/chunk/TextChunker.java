package com.sangui.raggateway.document.chunk;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {

    private final int chunkSize;
    private final int chunkOverlap;

    public TextChunker(int chunkSize, int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap must be non-negative");
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be less than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<String> chunk(String text) {
        if (text == null) {
            return List.of();
        }
        String cleaned = normalizeText(text);
        if (cleaned.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int length = cleaned.length();
        int pos = 0;

        while (pos < length) {
            int end = Math.min(pos + chunkSize, length);
            String chunk = cleaned.substring(pos, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            pos += (chunkSize - chunkOverlap);
            if (pos >= length) {
                break;
            }
        }

        return chunks;
    }

    private String normalizeText(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.trim();
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized;
    }
}
