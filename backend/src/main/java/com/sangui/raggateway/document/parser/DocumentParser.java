package com.sangui.raggateway.document.parser;

import java.io.InputStream;

public interface DocumentParser {
    boolean supports(String contentType, String filename);
    ParsedDocument parse(InputStream inputStream);
}
