package com.bupt.publicopinion.fake.exception;

public class FakeDetectionException extends RuntimeException {

    public FakeDetectionException(String message) {
        super(message);
    }

    public FakeDetectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
