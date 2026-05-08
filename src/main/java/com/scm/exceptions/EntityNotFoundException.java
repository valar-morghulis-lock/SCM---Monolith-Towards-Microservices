package com.scm.exceptions;

public class EntityNotFoundException extends BusinessException {
    public EntityNotFoundException(String message, String errorCode) {
        super(message, errorCode, 404);
    }
}