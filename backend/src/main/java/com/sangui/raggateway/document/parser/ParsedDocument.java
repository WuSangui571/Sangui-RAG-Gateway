package com.sangui.raggateway.document.parser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ParsedDocument {

    private final String text;
    private final String parserName;

    public ParsedDocument(String text, String parserName) {
        this.text = text;
        this.parserName = parserName;
    }

    public String getText() { return text; }
    public String getParserName() { return parserName; }

    public static String readUtf8(InputStream inputStream) {
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
