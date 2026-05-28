package com.sangui.raggateway.document.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextDocumentParserTest {

    private final PlainTextDocumentParser parser = new PlainTextDocumentParser();

    @Test
    void shouldSupportTxtExtension() {
        assertThat(parser.supports("text/plain", "readme.txt")).isTrue();
        assertThat(parser.supports("application/octet-stream", "notes.TXT")).isTrue();
    }

    @Test
    void shouldNotSupportOtherExtensions() {
        assertThat(parser.supports("text/markdown", "readme.md")).isFalse();
        assertThat(parser.supports("application/pdf", "doc.pdf")).isFalse();
    }

    @Test
    void shouldRejectNullFilename() {
        assertThat(parser.supports("text/plain", null)).isFalse();
    }

    @Test
    void shouldParseUtf8Text() {
        String content = "Hello World\nLine 2";
        ByteArrayInputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        ParsedDocument result = parser.parse(stream);

        assertThat(result.getText()).isEqualTo("Hello World\nLine 2");
        assertThat(result.getParserName()).isEqualTo("plain-text");
    }

    @Test
    void shouldParseEmptyFile() {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
        ParsedDocument result = parser.parse(stream);

        assertThat(result.getText()).isEmpty();
    }

    @Test
    void shouldParseUnicodeContent() {
        String content = "中文内容\n日本語";
        ByteArrayInputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        ParsedDocument result = parser.parse(stream);

        assertThat(result.getText()).isEqualTo("中文内容\n日本語");
    }
}
