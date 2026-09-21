package com.ruoyi.integration.reference.model;

/** Public-safe error; database exception messages must never become API messages. */
public class ReferenceException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final boolean retryable;
    private String executionId;

    public ReferenceException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, httpStatus == 503 || httpStatus == 504, null);
    }
    public ReferenceException(String code, int httpStatus, String message, boolean retryable) {
        this(code, httpStatus, message, retryable, null);
    }
    public ReferenceException(String code, int httpStatus, String message, Throwable cause) {
        this(code, httpStatus, message, httpStatus == 503 || httpStatus == 504, cause);
    }
    public ReferenceException(String code, int httpStatus, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }
    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
    public boolean retryable() { return retryable; }
    public String executionId() { return executionId; }
    public ReferenceException withExecutionId(String executionId) { this.executionId = executionId; return this; }
}
