package com.bank.digitalcore.carddispatch.domain;

/**
 * Namespace for typed dispatch errors mapped consistently at the API boundary.
 */
public final class DispatchExceptions {
    private DispatchExceptions() {
    }

    /**
     * Raised when a dispatch identifier is not registered or is not owned by the caller.
     */
    public static class DispatchNotFoundException extends RuntimeException {
        public DispatchNotFoundException() {
            super("Card dispatch was not found");
        }
    }

    /**
     * Raised when an identifier is already registered with different dispatch data.
     */
    public static class DispatchConflictException extends RuntimeException {
        public DispatchConflictException() {
            super("Dispatch identifier is already registered with different data");
        }
    }

    /**
     * Raised when a partner has no enabled tracking integration.
     */
    public static class PartnerNotConfiguredException extends RuntimeException {
        public PartnerNotConfiguredException() {
            super("Delivery partner integration is not configured");
        }
    }

    /**
     * Raised when a carrier cannot provide a reliable tracking response.
     */
    public static class PartnerUnavailableException extends RuntimeException {
        public PartnerUnavailableException() {
            super("Delivery partner tracking is temporarily unavailable");
        }
    }

    /**
     * Raised when the configured partner does not recognize a tracking number.
     */
    public static class PartnerTrackingNotFoundException extends RuntimeException {
        public PartnerTrackingNotFoundException() {
            super("Delivery partner has no tracking information for this dispatch");
        }
    }

    /**
     * Raised when a carrier does not support cancellation for the dispatch state.
     */
    public static class DispatchCancellationNotSupportedException extends RuntimeException {
        public DispatchCancellationNotSupportedException() {
            super("Dispatch cancellation is not supported");
        }
    }

    /**
     * Raised when a dispatch is already in a terminal state that cannot be canceled.
     */
    public static class DispatchNotCancelableException extends RuntimeException {
        public DispatchNotCancelableException() {
            super("Dispatch cannot be canceled in its current state");
        }
    }
}
