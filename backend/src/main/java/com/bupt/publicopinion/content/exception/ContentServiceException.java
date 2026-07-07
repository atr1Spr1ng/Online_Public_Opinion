package com.bupt.publicopinion.content.exception;

public class ContentServiceException extends RuntimeException {

    public ContentServiceException(String message) {
        super(message);
    }

    public ContentServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
