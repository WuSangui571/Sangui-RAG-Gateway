package com.sangui.raggateway.document.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownDocumentParserTest {

    private final MarkdownDocumentParser parser = new MarkdownDocumentParser();

    @Test
    void shouldSupportMdExtension() {
        assertThat(parser.supports("text/markdown", "readme.md")).isTrue();
        assertThat(parser.supports("application/octet-stream", "notes.MD")).isTrue();
    }

    @Test
    void shouldSupportMarkdownExtension() {
        assertThat(parser.supports("text/markdown", "doc.markdown")).isTrue();
    }

    @Test
    void shouldNotSupportOtherExtensions() {
        assertThat(parser.supports("text/plain", "readme.txt")).isFalse();
        assertThat(parser.supports("application/pdf", "doc.pdf")).isFalse();
    }

    @Test
    void shouldRejectNullFilename() {
        assertThat(parser.supports("text/markdown", null)).isFalse();
    }

    @Test
    void shouldParseMarkdownAsUtf8Text() {
        String content = "# Title\n\nThis is **markdown** content.";
        ByteArrayInputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        ParsedDocument result = parser.parse(stream);

        assertThat(result.getText()).contains("# Title");
        assertThat(result.getText()).contains("**markdown**");
        assertThat(result.getParserName()).isEqualTo("markdown");
    }

    @Test
    void shouldParseEmptyMarkdown() {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
        ParsedDocument result = parser.parse(stream);

        assertThat(result.getText()).isEmpty();
    }
}
