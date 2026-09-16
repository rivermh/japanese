package com.japanese.content.service;

public class ContentReleaseBatchException extends IllegalStateException {
    private final String code;
    public ContentReleaseBatchException(String code) { this(code, code); }
    public ContentReleaseBatchException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
