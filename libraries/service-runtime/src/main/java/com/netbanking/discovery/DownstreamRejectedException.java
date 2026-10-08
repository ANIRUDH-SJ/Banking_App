package com.netbanking.discovery;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/**
 * A downstream service answered with an error. The reason stays generic so internal messages are
 * not relayed by default; a client that owns the contract can read {@link #code()} and {@link
 * #detail()} to translate a known outcome.
 */
public class DownstreamRejectedException extends ResponseStatusException {
    private final String code;
    private final String detail;
    private final long retryAfterSeconds;

    public DownstreamRejectedException(
            HttpStatusCode status, String code, String detail, long retryAfterSeconds) {
        super(status, "Downstream service rejected the operation.");
        this.code = code;
        this.detail = detail;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() {
        return code;
    }

    public String detail() {
        return detail;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
