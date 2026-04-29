package com.scm.exceptions.domains.inventory;

import com.scm.exceptions.BusinessException;

public class DuplicateResourceException extends BusinessException {
    public DuplicateResourceException(String message) {
        super(message, "DUPLICATE_ENTRY", 409);
    }
}
