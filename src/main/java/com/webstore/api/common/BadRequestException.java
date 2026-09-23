package com.webstore.api.common;

/** The request refers to something that doesn't exist or is otherwise invalid (HTTP 400). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
