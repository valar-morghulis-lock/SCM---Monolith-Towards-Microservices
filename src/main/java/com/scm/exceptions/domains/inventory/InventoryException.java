package com.scm.exceptions.domains.inventory;

import com.scm.exceptions.BusinessException;

public class InventoryException extends BusinessException {
    public InventoryException(String errorCode, String message, int status) {
        super(message, errorCode, status);
    }
}