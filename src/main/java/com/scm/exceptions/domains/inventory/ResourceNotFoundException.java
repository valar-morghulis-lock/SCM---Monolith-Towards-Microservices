package com.scm.exceptions.domains.inventory;

import com.scm.exceptions.BusinessException;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource, Object id) {
        super(String.format("%s with identifier [%s] was not found.", resource, id),
                "RESOURCE_NOT_FOUND", 404);
    }
}

