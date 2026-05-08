package com.scm.exceptions;


import com.scm.exceptions.domains.inventory.InsufficientStockException;
import org.jooq.exception.DataAccessException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;


@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, WebRequest req) {
        log.warn("Business Exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), req);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabase(DataAccessException ex, WebRequest req) {
        log.warn("Database Error: {}", ex.getMessage());
        return build(500, "DATABASE_ERROR", "A technical database error occurred.", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest req) {
        String details = ex.getBindingResult().getFieldErrors().stream().map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining(", "));
        log.warn("Validation Failed: {}", details);
        return build(400, "VALIDATION_FAILED", details, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex, WebRequest req) {
        log.warn("Uncaught Exception: ", ex); // Log stack trace for 500s
        return build(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred.", req);
    }

    private ResponseEntity<ErrorResponse> build(int status, String error, String msg, WebRequest req) {
        // req.getDescription(false) handles the "uri=" prefixing for you
        var response = new ErrorResponse(
                LocalDateTime.now(),
                status,
                error,
                msg,
                req.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(
            InsufficientStockException ex,
            WebRequest request) { // Changed from HttpServletRequest to WebRequest

        // Using your preferred .warn level
        log.warn("Inventory Conflict for Product {}: Requested {}, Available {}",
                ex.getProductId(), ex.getRequestedQuantity(), ex.getAvailableQuantity());

        String detailedMessage = String.format(
                "%s (Requested: %d, Available: %d)",
                ex.getMessage(),
                ex.getRequestedQuantity() != null ? ex.getRequestedQuantity() : 0,
                ex.getAvailableQuantity() != null ? ex.getAvailableQuantity() : 0
        );

        // Use your existing 'build' helper to keep the logic DRY
        return build(
                HttpStatus.CONFLICT.value(),
                "INSUFFICIENT_STOCK",
                detailedMessage,
                request
        );
    }

    @ExceptionHandler(com.fasterxml.jackson.databind.exc.InvalidFormatException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFormat(
            com.fasterxml.jackson.databind.exc.InvalidFormatException ex,
            WebRequest req) {

        String field = ex.getPath().stream()
                .map(ref -> ref.getFieldName())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("."));

        String msg = String.format("Invalid value for field '%s': %s", field, ex.getValue());

        log.warn("Deserialization Failed: {}", msg); // Using your preferred .warn level

        return build(400, "INVALID_FORMAT", msg, req);
    }
}