package com.sangui.raggateway.document;

import java.util.Locale;
import java.util.Set;

public final class DocumentUploadRules {

    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "text/plain",
            "text/markdown",
            "application/octet-stream"
    );

    private DocumentUploadRules() {
    }

    public static boolean isSupportedFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    public static boolean isSupportedContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        int separatorIndex = normalized.indexOf(';');
        if (separatorIndex >= 0) {
            normalized = normalized.substring(0, separatorIndex);
        }
        return SUPPORTED_CONTENT_TYPES.contains(normalized.trim());
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "untitled";
        }
        String name = extractBasename(filename);
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    public static String extractDisplayBasename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "untitled";
        }
        return extractBasename(filename);
    }

    private static String extractBasename(String filename) {
        String normalized = filename.replace('\\', '/');
        int separatorIndex = normalized.lastIndexOf('/');
        String basename = separatorIndex >= 0 ? normalized.substring(separatorIndex + 1) : normalized;
        return basename.isBlank() ? "untitled" : basename;
    }
}
