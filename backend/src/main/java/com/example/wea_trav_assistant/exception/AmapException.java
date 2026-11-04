package com.example.wea_trav_assistant.exception;

public class AmapException extends RuntimeException {
    private final AmapErrorCode code;

    public AmapException(AmapErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AmapErrorCode getCode() { return code; }
}
