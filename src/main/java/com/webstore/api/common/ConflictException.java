package com.webstore.api.common;

/** The request is valid but conflicts with existing data (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
