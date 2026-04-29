package com.scm.exceptions.domains.inventory;

import com.scm.exceptions.BusinessException;

// Use for stock issues or invalid order states
public class InvalidOperationException extends BusinessException {
    public InvalidOperationException(String message, String errorCode) {
        super(message, errorCode, 400);
    }
}
