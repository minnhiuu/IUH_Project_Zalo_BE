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
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    private final LocalizationUtil localizationUtil;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        
        String errorKey = errorCode.getMessageKey();
        String errorMessage = localizationUtil.getMessage(errorKey);
        
        Map<String, String> errors = new HashMap<>();
        errors.put(errorCode.name(), errorMessage);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorMessage, errors));
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

        String errorKey = errorCode.getMessageKey();
        String errorMessage = localizationUtil.getMessage(errorKey);
        
        Map<String, String> errors = new HashMap<>();
        errors.put(errorCode.name(), errorMessage);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorMessage, errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception) {
            
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        
        Map<String, String> errors = new HashMap<>();
        errors.put("payload", "Invalid request payload or malformed JSON");

        String message = localizationUtil.getMessage(errorCode.getMessageKey());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), message, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        Map<String, String> errors = new HashMap<>();
        
        exception.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            errors.put(fieldName, errorMessage);
        });

        String message = localizationUtil.getMessage(errorCode.getMessageKey());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), message, errors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception) {
            
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        
        Map<String, String> errors = new HashMap<>();
        errors.put(exception.getParameterName(), "Required parameter is missing");

        String message = localizationUtil.getMessage(errorCode.getMessageKey());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), message, errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
            
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        
        Map<String, String> errors = new HashMap<>();
        errors.put(exception.getName(), "Invalid parameter type: " + exception.getValue());

        String message = localizationUtil.getMessage(errorCode.getMessageKey());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), message, errors));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleNoHandlerFoundException(
            NoHandlerFoundException exception) {
            
        ErrorCode errorCode = ErrorCode.SYS_UNCATEGORIZED; 
        
        Map<String, String> errors = new HashMap<>();
        errors.put("path", "Endpoint not found: " + exception.getRequestURL());

        String message = localizationUtil.getMessage(errorCode.getMessageKey());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(errorCode.getCode(), message, errors));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception) {
            
        ErrorCode errorCode = ErrorCode.SYS_UNCATEGORIZED; 
        
        Map<String, String> errors = new HashMap<>();
        errors.put("method", "HTTP method not supported: " + exception.getMethod());

        String message = localizationUtil.getMessage(errorCode.getMessageKey());

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(errorCode.getCode(), message, errors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleAccessDeniedException(
            AccessDeniedException exception) {
            
        ErrorCode errorCode = ErrorCode.AUTH_UNAUTHORIZED; 
        
        String errorKey = errorCode.getMessageKey();
        String errorMessage = localizationUtil.getMessage(errorKey);
        
        Map<String, String> errors = new HashMap<>();
        errors.put(errorCode.name(), errorMessage);

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorMessage, errors));
    }
}