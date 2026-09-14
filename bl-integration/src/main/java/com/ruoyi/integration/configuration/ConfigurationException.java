package com.ruoyi.integration.configuration;

/** A safe, user-facing failure for configuration administration. */
public class ConfigurationException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final String requestId;

    public ConfigurationException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    public ConfigurationException(String code, int httpStatus, String message, String requestId) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.requestId = requestId;
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String requestId() {
        return requestId;
    }
}
