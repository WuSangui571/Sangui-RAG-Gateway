package com.sangui.raggateway.common.util;

import java.util.Locale;

public final class PgVectorFormatter {

    private PgVectorFormatter() {
    }

    public static String format(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("Vector must not be null");
        }
        if (vector.length == 0) {
            throw new IllegalArgumentException("Vector must not be empty");
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i])) {
                throw new IllegalArgumentException("Vector component at index " + i + " must be finite");
            }
            if (i > 0) {
                sb.append(",");
            }
            sb.append(String.format(Locale.ROOT, "%.8f", vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
