package com.bupt.publicopinion.collection.exception;

public class CrawlerServiceException extends RuntimeException {

    public CrawlerServiceException(String message) {
        super(message);
    }

    public CrawlerServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
