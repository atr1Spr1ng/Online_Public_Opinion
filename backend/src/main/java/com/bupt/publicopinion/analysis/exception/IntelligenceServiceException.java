package com.bupt.publicopinion.analysis.exception;

public class IntelligenceServiceException extends RuntimeException {

    public IntelligenceServiceException(String message) {
        super(message);
    }

    public IntelligenceServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
