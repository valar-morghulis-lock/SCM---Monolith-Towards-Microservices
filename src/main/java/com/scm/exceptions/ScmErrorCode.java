package com.scm.exceptions;

public enum ScmErrorCode {
    RESOURCE_NOT_FOUND("SCM-404"),
    INSUFFICIENT_FUNDS("SCM-102"),
    DATABASE_TIMEOUT("SCM-503");

    private final String code;
    ScmErrorCode(String code) { this.code = code; }
    public String getCode() { return code; }
}
