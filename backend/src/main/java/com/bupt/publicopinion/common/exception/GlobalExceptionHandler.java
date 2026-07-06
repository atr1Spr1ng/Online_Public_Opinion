package com.bupt.publicopinion.common.exception;

import com.bupt.publicopinion.collection.exception.CrawlerServiceException;
import com.bupt.publicopinion.common.vo.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CrawlerServiceException.class)
    public ResponseEntity<ErrorResponse> handleCrawlerServiceException(
            CrawlerServiceException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                exception.getMessage(),
                OffsetDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }
}
