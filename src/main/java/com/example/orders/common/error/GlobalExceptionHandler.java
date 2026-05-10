package com.example.orders.common.error;

import com.example.orders.common.logging.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(OrderNotFoundException ex, HttpServletRequest req) {
        log.warn("Order not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Order not found", ex.getMessage(), ErrorCodes.ORDER_NOT_FOUND, req);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTransition(InvalidStateTransitionException ex, HttpServletRequest req) {
        log.warn("Invalid state transition: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Invalid state transition", ex.getMessage(),
                ErrorCodes.ORDER_INVALID_STATE_TRANSITION, req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        log.warn("Concurrent modification: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Concurrent modification", "Order was modified concurrently. Retry the request.",
                ErrorCodes.ORDER_CONCURRENT_MODIFICATION, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failure: {}", detail);
        return build(HttpStatus.BAD_REQUEST, "Validation failed", detail, ErrorCodes.VALIDATION_ERROR, req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Authentication required", ex.getMessage(),
                ErrorCodes.AUTHENTICATION_REQUIRED, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), ErrorCodes.FORBIDDEN, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleFallback(Exception ex, HttpServletRequest req, WebRequest webRequest) {
        log.error("Unhandled error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred", ErrorCodes.INTERNAL_ERROR, req);
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String title, String detail,
                                                String errorCode, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setInstance(java.net.URI.create(req.getRequestURI()));
        pd.setProperty("errorCode", errorCode);
        pd.setProperty("timestamp", Instant.now().toString());
        pd.setProperty("traceId", MDC.get(TraceIdFilter.TRACE_ID));
        return ResponseEntity.status(status).body(pd);
    }
}
