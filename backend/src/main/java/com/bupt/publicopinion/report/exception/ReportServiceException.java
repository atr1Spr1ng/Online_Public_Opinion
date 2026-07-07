package com.bupt.publicopinion.report.exception;

public class ReportServiceException extends RuntimeException {

    public ReportServiceException(String message) {
        super(message);
    }

    public ReportServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
