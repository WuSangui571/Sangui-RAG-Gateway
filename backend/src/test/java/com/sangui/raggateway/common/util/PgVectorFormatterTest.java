package com.sangui.raggateway.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgVectorFormatterTest {

    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void shouldFormatNormalVectorWithFixedEightDecimals() {
        float[] vector = {0.1f, 0.2f, -0.3f};
        String result = PgVectorFormatter.format(vector);

        assertThat(result).isEqualTo("[0.10000000,0.20000000,-0.30000001]");
    }

    @Test
    void shouldFormatSingleElementVector() {
        float[] vector = {42.5f};
        String result = PgVectorFormatter.format(vector);

        assertThat(result).isEqualTo("[42.50000000]");
    }

    @Test
    void shouldNotContainSpaces() {
        float[] vector = {1.0f, 2.0f, 3.0f};
        String result = PgVectorFormatter.format(vector);

        assertThat(result).doesNotContain(" ");
    }

    @Test
    void shouldStartAndEndWithBrackets() {
        float[] vector = {1.0f};
        String result = PgVectorFormatter.format(vector);

        assertThat(result).startsWith("[");
        assertThat(result).endsWith("]");
    }

    @Test
    void shouldHaveNoTrailingCommaForSingleElement() {
        float[] vector = {1.0f};
        String result = PgVectorFormatter.format(vector);

        assertThat(result).isEqualTo("[1.00000000]");
        assertThat(result).doesNotContainPattern(",\\]");
    }

    @Test
    void shouldRejectNullVector() {
        assertThatThrownBy(() -> PgVectorFormatter.format(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void shouldRejectEmptyVector() {
        assertThatThrownBy(() -> PgVectorFormatter.format(new float[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void shouldUseLocaleRootDecimalSeparator() {
        Locale.setDefault(Locale.GERMANY);
        float[] vector = {0.1f, 0.2f};
        String result = PgVectorFormatter.format(vector);

        assertThat(result).isEqualTo("[0.10000000,0.20000000]");
    }

    @Test
    void shouldHandleFiniteExtremeValues() {
        float[] vector = {Float.MAX_VALUE, Float.MIN_VALUE, -Float.MIN_VALUE};
        String result = PgVectorFormatter.format(vector);

        assertThat(result).startsWith("[");
        assertThat(result).endsWith("]");
        assertThat(result).contains(".");
        assertThat(result).doesNotContain("NaN", "Infinity");
    }

    @Test
    void shouldRejectNaNComponent() {
        assertThatThrownBy(() -> PgVectorFormatter.format(new float[]{1.0f, Float.NaN}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("index 1")
                .hasMessageContaining("finite");
    }

    @Test
    void shouldRejectInfiniteComponent() {
        assertThatThrownBy(() -> PgVectorFormatter.format(new float[]{Float.POSITIVE_INFINITY}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("index 0")
                .hasMessageContaining("finite");
    }

    @Test
    void shouldBeDeterministic() {
        float[] vector = {0.1f, 0.2f, 0.3f};
        String first = PgVectorFormatter.format(vector);
        String second = PgVectorFormatter.format(vector);

        assertThat(first).isEqualTo(second);
    }
}
