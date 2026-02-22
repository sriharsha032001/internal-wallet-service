package com.internal_wallet.internal_wallet.exception;

import com.internal_wallet.internal_wallet.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        // HttpStatus.UNPROCESSABLE_ENTITY is deprecated in Spring 7, using valueOf directly
        return build(HttpStatusCode.valueOf(422), "Insufficient Balance", ex.getMessage());
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotFound(WalletNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Wallet Not Found", ex.getMessage());
    }

    @ExceptionHandler(AssetTypeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAssetTypeNotFound(AssetTypeNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Asset Type Not Found", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("One or more fields failed validation")
                .fieldErrors(fieldErrors)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath().toString()
                        .replaceAll(".*\\.", "") // strip method/param prefix, keep param name
                        + ": " + cv.getMessage())
                .findFirst()
                .orElse("Invalid request parameter");
        return build(HttpStatus.BAD_REQUEST, "Validation Failed", message);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(HttpStatus.BAD_REQUEST, "Missing Header",
                "Required header '" + ex.getHeaderName() + "' is missing");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed Request Body",
                "The request body could not be parsed. Check JSON syntax and field types.");
    }

    // lock_timeout (3s) fired — another transaction holds the row lock, safe to retry
    @ExceptionHandler(org.springframework.dao.CannotAcquireLockException.class)
    public ResponseEntity<ErrorResponse> handleCannotAcquireLock(
            org.springframework.dao.CannotAcquireLockException ex) {
        log.warn("Lock contention: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatusCode.valueOf(503), "Service Temporarily Unavailable",
                "Resource locked by a concurrent request. Retry with the same Idempotency-Key.");
    }

    // statement_timeout (10s) or @Transactional timeout (15s) fired — safe to retry
    @ExceptionHandler(org.springframework.dao.QueryTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleQueryTimeout(
            org.springframework.dao.QueryTimeoutException ex) {
        log.warn("Query timeout: {}", ex.getMessage());
        return build(HttpStatusCode.valueOf(503), "Service Temporarily Unavailable",
                "Request timed out. Retry with the same Idempotency-Key.");
    }

    // idempotency duplicates are resolved in the controller before reaching here,
    // so any DataIntegrityViolationException at this point is a real conflict
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            org.springframework.dao.DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "Conflict", "A data conflict occurred. " + ex.getMostSpecificCause().getMessage());
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(
            org.springframework.dao.DataAccessException ex) {
        log.error("DataAccessException [{}]: {}", ex.getClass().getName(), ex.getMessage(), ex);
        Throwable root = ex.getMostSpecificCause();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Database Error",
                root.getClass().getSimpleName() + ": " + root.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception [{}]: {}", ex.getClass().getName(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatusCode statusCode, String error, String message) {
        ErrorResponse body = ErrorResponse.builder()
                .status(statusCode.value())
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
        return ResponseEntity.status(statusCode).body(body);
    }
}
