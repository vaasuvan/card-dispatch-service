package com.bank.digitalcore.carddispatch.api;

import com.bank.digitalcore.carddispatch.domain.DispatchExceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static com.bank.digitalcore.carddispatch.api.CardDispatchConstants.*;

/**
 * Maps domain and infrastructure failures to stable, PII-safe HTTP responses.
 */
@RestControllerAdvice
public class CardDispatchExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(CardDispatchExceptionHandler.class);

    /**
     * Returns a not-found response for unknown bank dispatch or carrier tracking identifiers.
     */
    @ExceptionHandler({DispatchNotFoundException.class, PartnerTrackingNotFoundException.class})
    public ResponseEntity<CardDispatchApiError> notFound(RuntimeException exception, WebRequest request) {
        String code = exception instanceof DispatchNotFoundException
                ? ERROR_DISPATCH_NOT_FOUND : ERROR_PARTNER_TRACKING_NOT_FOUND;
        return response(HttpStatus.NOT_FOUND, code, exception.getMessage(), request);
    }

    /**
     * Returns a conflict for a duplicate registration carrying different dispatch details.
     */
    @ExceptionHandler(DispatchConflictException.class)
    public ResponseEntity<CardDispatchApiError> conflict(RuntimeException exception, WebRequest request) {
        log.warn("Card dispatch request conflicted errorType={}", exception.getClass().getSimpleName());
        return response(HttpStatus.CONFLICT, ERROR_DISPATCH_CONFLICT, exception.getMessage(), request);
    }

    /**
     * Returns a conflict when the carrier or current lifecycle state cannot be canceled.
     */
    @ExceptionHandler(DispatchNotCancelableException.class)
    public ResponseEntity<CardDispatchApiError> notCancelable(RuntimeException exception, WebRequest request) {
        return response(HttpStatus.CONFLICT, ERROR_DISPATCH_NOT_CANCELABLE, exception.getMessage(), request);
    }

    /**
     * Reports carrier-specific cancellation limitations with a stable API code.
     */
    @ExceptionHandler(DispatchCancellationNotSupportedException.class)
    public ResponseEntity<CardDispatchApiError> cancellationNotSupported(
            RuntimeException exception,
            WebRequest request
    ) {
        return response(HttpStatus.NOT_IMPLEMENTED, ERROR_CANCELLATION_NOT_SUPPORTED,
                exception.getMessage(), request);
    }

    /**
     * Reports unavailable or unconfigured delivery integrations without exposing partner internals.
     */
    @ExceptionHandler({PartnerUnavailableException.class, PartnerNotConfiguredException.class})
    public ResponseEntity<CardDispatchApiError> partnerUnavailable(RuntimeException exception, WebRequest request) {
        String code = exception instanceof PartnerNotConfiguredException
                ? ERROR_PARTNER_NOT_CONFIGURED : ERROR_PARTNER_UNAVAILABLE;
        log.warn("Delivery partner request failed code={} errorType={}", code,
                exception.getClass().getSimpleName());
        return response(HttpStatus.SERVICE_UNAVAILABLE, code, exception.getMessage(), request);
    }

    /**
     * Returns validation errors without echoing potentially sensitive request fields.
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class,
            IllegalArgumentException.class,
            jakarta.validation.ConstraintViolationException.class
    })
    public ResponseEntity<CardDispatchApiError> badRequest(Exception exception, WebRequest request) {
        log.warn("Invalid card dispatch request errorType={} path={}",
                exception.getClass().getSimpleName(), requestPath(request));
        return response(HttpStatus.BAD_REQUEST, ERROR_INVALID_REQUEST,
                "Request validation failed", request);
    }

    /**
     * Returns a temporary storage failure while logging no SQL or customer data.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<CardDispatchApiError> storageUnavailable(DataAccessException exception, WebRequest request) {
        log.error("Card dispatch database operation failed errorType={}", exception.getClass().getSimpleName());
        return response(HttpStatus.SERVICE_UNAVAILABLE, ERROR_STORAGE_UNAVAILABLE,
                "Card dispatch service is temporarily unavailable", request);
    }

    /**
     * Returns a generic failure and keeps internal exception details out of the API response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CardDispatchApiError> internalError(Exception exception, WebRequest request) {
        log.error("Unhandled card dispatch API error errorType={}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INTERNAL,
                "An unexpected error occurred", request);
    }

    private ResponseEntity<CardDispatchApiError> response(
            HttpStatus status,
            String code,
            String message,
            WebRequest request
    ) {
        String path = requestPath(request);
        return ResponseEntity.status(status).body(new CardDispatchApiError(
                java.time.Instant.now(), code, message, path, org.slf4j.MDC.get("correlationId")));
    }

    private String requestPath(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI() : "/";
    }
}
