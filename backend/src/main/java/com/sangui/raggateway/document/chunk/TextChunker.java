package com.sangui.raggateway.document.chunk;

import com.sangui.raggateway.document.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {

    private final int chunkSize;
    private final int chunkOverlap;
    private final TextNormalizer textNormalizer;

    public TextChunker(int chunkSize, int chunkOverlap, TextNormalizer textNormalizer) {
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
        this.textNormalizer = textNormalizer;
    }

    public List<String> chunk(String text) {
        if (text == null) {
            return List.of();
        }
        String cleaned = textNormalizer.normalize(text);
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
}
