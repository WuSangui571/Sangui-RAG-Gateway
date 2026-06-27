package com.sangui.raggateway.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(normalizer.normalize(null)).isNull();
    }

    @Test
    void shouldReturnBlankForWhitespaceOnly() {
        assertThat(normalizer.normalize("   \t   \n  ")).isBlank();
    }

    @Test
    void shouldReturnEmptyForEmptyString() {
        assertThat(normalizer.normalize("")).isEmpty();
    }

    @Test
    void shouldReplaceCRLFWithLF() {
        String result = normalizer.normalize("Hello\r\nWorld");
        assertThat(result).isEqualTo("Hello\nWorld");
    }

    @Test
    void shouldReplaceCRWithLF() {
        String result = normalizer.normalize("Hello\rWorld");
        assertThat(result).isEqualTo("Hello\nWorld");
    }

    @Test
    void shouldCollapseThreeOrMoreLFToTwoLF() {
        String result = normalizer.normalize("Line1\n\n\n\n\nLine2");
        assertThat(result).isEqualTo("Line1\n\nLine2");
    }

    @Test
    void shouldKeepTwoLFUnchanged() {
        String result = normalizer.normalize("Line1\n\nLine2");
        assertThat(result).isEqualTo("Line1\n\nLine2");
    }

    @Test
    void shouldKeepSingleLFUnchanged() {
        String result = normalizer.normalize("Line1\nLine2");
        assertThat(result).isEqualTo("Line1\nLine2");
    }

    @Test
    void shouldTrimLeadingAndTrailingWhitespace() {
        String result = normalizer.normalize("  \n  Hello World  \n  ");
        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    void shouldPreserveChineseTextContent() {
        String result = normalizer.normalize("你好世界\n\n这是测试内容");
        assertThat(result).isEqualTo("你好世界\n\n这是测试内容");
    }

    @Test
    void shouldNormalizeCRLFInChineseText() {
        String result = normalizer.normalize("你好世界\r\n\r\n\r\n\r\n这是测试内容");
        assertThat(result).isEqualTo("你好世界\n\n这是测试内容");
    }

    @Test
    void shouldPreserveEnglishTextContent() {
        String result = normalizer.normalize("Hello World\n\nThis is test content");
        assertThat(result).isEqualTo("Hello World\n\nThis is test content");
    }

    @Test
    void shouldPreserveMixedChineseEnglishContent() {
        String result = normalizer.normalize("English 中文 mixed content\n\n第二段 second paragraph");
        assertThat(result).isEqualTo("English 中文 mixed content\n\n第二段 second paragraph");
    }

    @Test
    void shouldPreserveInternalTabsAndSpaces() {
        String result = normalizer.normalize("Col1\tCol2\tCol3\n\nData with  spaces  inside");
        assertThat(result).isEqualTo("Col1\tCol2\tCol3\n\nData with  spaces  inside");
    }

    @Test
    void shouldNotTruncateLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("Line ").append(i).append("\n\n");
        }
        String input = sb.toString();
        String result = normalizer.normalize(input);
        assertThat(result).isNotNull();
        assertThat(result.length()).isGreaterThanOrEqualTo(input.length() - 10000);
    }

    @Test
    void shouldHandleTextWithOnlyNewlines() {
        String result = normalizer.normalize("\n\n\n\n\n");
        assertThat(result).isBlank();
    }

    @Test
    void shouldHandleTextWithMixedLineEndings() {
        String result = normalizer.normalize("A\r\nB\rC\nD\r\n\r\n\r\n\r\nE");
        assertThat(result).isEqualTo("A\nB\nC\nD\n\nE");
    }
}
