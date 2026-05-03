package com.banking.handoff.exception;

public class HandoffException extends RuntimeException {

    public HandoffException(String message) {
        super(message);
    }

    public HandoffException(String message, Throwable cause) {
        super(message, cause);
    }
}
