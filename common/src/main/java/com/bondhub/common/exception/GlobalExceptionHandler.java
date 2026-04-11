package com.bondhub.common.exception;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.LocalizationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    private final LocalizationUtil localizationUtil;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        String message = localizationUtil.getMessage(errorCode.getMessageKey());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), message, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException exception) {

        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        Map<String, String> errors = new HashMap<>();

        exception.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : error.getObjectName();

            String errorKey = error.getDefaultMessage();
            String errorMessage = localizationUtil.getMessage(errorKey);

            errors.put(fieldName, errorMessage);
        });

        String message = localizationUtil.getMessage(errorCode.getMessageKey());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), message, errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleUncategorizedException(
            Exception exception) {

        ErrorCode errorCode = ErrorCode.SYS_UNCATEGORIZED;

        exception.printStackTrace();

        String message = localizationUtil.getMessage(errorCode.getMessageKey());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), message, null));
    }
}