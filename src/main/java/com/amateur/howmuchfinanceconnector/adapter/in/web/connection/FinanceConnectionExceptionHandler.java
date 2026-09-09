package com.amateur.howmuchfinanceconnector.adapter.in.web.connection;

import com.amateur.howmuchfinanceconnector.application.connection.FinanceConnectionAccountConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = FinanceConnectionController.class)
public class FinanceConnectionExceptionHandler {

    @ExceptionHandler(FinanceConnectionAccountConflictException.class)
    ResponseEntity<ErrorResponse> accountConflict(FinanceConnectionAccountConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("FINANCE_CONNECTION_ACCOUNT_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("FINANCE_CONNECTION_INVALID_REQUEST", exception.getMessage()));
    }

    record ErrorResponse(String code, String message) {
    }
}
