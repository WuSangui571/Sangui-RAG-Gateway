package com.sangui.raggateway.document.parser;

import java.io.InputStream;

public class PlainTextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType, String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt");
    }

    @Override
    public ParsedDocument parse(InputStream inputStream) {
        String text = ParsedDocument.readUtf8(inputStream);
        return new ParsedDocument(text, "plain-text");
    }
}
