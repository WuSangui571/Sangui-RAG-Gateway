package com.sangui.raggateway.document.chunk;

import com.sangui.raggateway.document.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextChunkerTest {

    @Test
    void shouldCreateSingleChunkForShortText() {
        TextChunker chunker = new TextChunker(800, 100, new TextNormalizer());
        List<String> chunks = chunker.chunk("Hello World");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("Hello World");
    }

    @Test
    void shouldCreateMultipleChunksForLongText() {
        TextChunker chunker = new TextChunker(10, 2, new TextNormalizer());
        String text = "0123456789ABCDEFGHIJKLMNOP";
        List<String> chunks = chunker.chunk(text);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThan(1);
    }

    @Test
    void shouldProduceOverlappingChunks() {
        TextChunker chunker = new TextChunker(20, 5, new TextNormalizer());
        String text = "AAAAAAAAAABBBBBBBBBBCCCCCCCCCCDDDDDDDDDDEEEEEEEEEE";
        List<String> chunks = chunker.chunk(text);
        assertThat(chunks).hasSizeGreaterThan(2);
    }

    @Test
    void shouldPreserveDeterministicOrder() {
        TextChunker chunker = new TextChunker(20, 5, new TextNormalizer());
        String text = "AAAAAAAAAABBBBBBBBBBCCCCCCCCCCDDDDDDDDDD";
        List<String> first = chunker.chunk(text);
        List<String> second = chunker.chunk(text);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldSkipBlankChunks() {
        TextChunker chunker = new TextChunker(100, 10, new TextNormalizer());
        List<String> chunks = chunker.chunk("\n\n\n");
        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNullInput() {
        TextChunker chunker = new TextChunker(100, 10, new TextNormalizer());
        List<String> chunks = chunker.chunk(null);
        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldReturnEmptyForWhitespaceOnly() {
        TextChunker chunker = new TextChunker(100, 10, new TextNormalizer());
        List<String> chunks = chunker.chunk("   \t   \n  ");
        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldRejectNonPositiveChunkSize() {
        assertThatThrownBy(() -> new TextChunker(0, 10, new TextNormalizer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkSize must be positive");
    }

    @Test
    void shouldRejectNegativeOverlap() {
        assertThatThrownBy(() -> new TextChunker(100, -1, new TextNormalizer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkOverlap must be non-negative");
    }

    @Test
    void shouldRejectOverlapEqualToOrGreaterThanChunkSize() {
        assertThatThrownBy(() -> new TextChunker(100, 100, new TextNormalizer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkOverlap must be less than chunkSize");

        assertThatThrownBy(() -> new TextChunker(100, 150, new TextNormalizer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunkOverlap must be less than chunkSize");
    }
}
