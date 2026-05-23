package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.adapter.dto.ApiErrorResponse;
import com.fiap.hackathon.upload_service.config.observability.TraceSupport;
import com.fiap.hackathon.upload_service.service.AnalysisCallbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return errorResponse(ex, ex.getCode(), HttpStatus.NOT_FOUND, "Resource not found", ex.getDetail());
    }

    @ExceptionHandler(UploadValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadValidation(UploadValidationException ex) {
        return errorResponse(ex, ex.getCode(), HttpStatus.BAD_REQUEST, "Invalid upload request", ex.getDetail());
    }

    @ExceptionHandler(AnalysisCallbackService.UploadNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadNotFound(AnalysisCallbackService.UploadNotFoundException ex) {
        return errorResponse(ex, ex.getCode(), HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(AnalysisCallbackService.UploadConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadConflict(AnalysisCallbackService.UploadConflictException ex) {
        return errorResponse(ex, ex.getCode(), HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return errorResponse(
                ex,
                "VALIDATION_ERROR",
                HttpStatus.BAD_REQUEST,
                "Invalid request payload",
                detail.isBlank() ? "Validation failed" : detail);
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        return errorResponse(
                ex,
                "BAD_REQUEST",
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                ex.getMessage() == null ? "Request could not be processed" : ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return errorResponse(
                ex,
                "FILE_SIZE_EXCEEDED",
                HttpStatus.BAD_REQUEST,
                "Invalid upload request",
                "File size exceeds maximum allowed size of 8MB");
    }

    @ExceptionHandler({
            HttpMediaTypeNotSupportedException.class,
            HttpMessageNotReadableException.class,
            HttpMessageConversionException.class,
            MultipartException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMultipartParsing(Exception ex) {
        return errorResponse(
                ex,
                "INVALID_MULTIPART_METADATA",
                HttpStatus.BAD_REQUEST,
                "Invalid upload request",
                "The metadata part must be valid JSON with Content-Type application/json");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        return errorResponse(
                ex,
                "INTERNAL_ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error",
                "An unexpected error occurred while processing the request");
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            Exception ex,
            String code,
            HttpStatus status,
            String message,
            String detail) {
        TraceSupport.addErrorToSpan(ex, code);
        TraceSupport.putErrorMdc(ex, code, status.value());
        log.error("{} - Code: {} - Detail: {}", ex.getClass().getSimpleName(), code, detail, ex);
        TraceSupport.clearErrorMdc();

        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(message, code, detail));
    }
}
