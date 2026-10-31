package com.bank.digitalcore.carddispatch.api;

/**
 * Shared API constants for routing, correlation, and stable error codes.
 */
public final class CardDispatchConstants {
    public static final String API_BASE_PATH = "/api/v1/card-dispatches";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACKING_PATH = "/{dispatchId}/tracking";
    public static final String ERROR_DISPATCH_NOT_FOUND = "DISPATCH_NOT_FOUND";
    public static final String ERROR_PARTNER_TRACKING_NOT_FOUND = "PARTNER_TRACKING_NOT_FOUND";
    public static final String ERROR_DISPATCH_CONFLICT = "DISPATCH_CONFLICT";
    public static final String ERROR_PARTNER_NOT_CONFIGURED = "PARTNER_NOT_CONFIGURED";
    public static final String ERROR_PARTNER_UNAVAILABLE = "PARTNER_UNAVAILABLE";
    public static final String ERROR_INVALID_REQUEST = "INVALID_REQUEST";
    public static final String ERROR_DISPATCH_NOT_CANCELABLE = "DISPATCH_NOT_CANCELABLE";
    public static final String ERROR_CANCELLATION_NOT_SUPPORTED = "CANCELLATION_NOT_SUPPORTED";
    public static final String ERROR_STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";
    public static final String ERROR_INTERNAL = "INTERNAL_ERROR";

    private CardDispatchConstants() {
    }
}
