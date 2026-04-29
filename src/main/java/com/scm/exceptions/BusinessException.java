package com.scm.exceptions;


public abstract class BusinessException extends RuntimeException {
    private final String errorCode;
    private final int status;

    protected BusinessException(String message, String errorCode, int status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatus() {
        return status;
    }
}