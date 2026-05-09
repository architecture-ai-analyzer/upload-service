package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.adapter.dto.ApiErrorResponse;
import com.fiap.hackathon.upload_service.service.AnalysisCallbackService;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(new ApiErrorResponse(
                                                "Resource not found",
                                                ex.getCode(),
                                                ex.getDetail()
                                ));
        }

    @ExceptionHandler(UploadValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadValidation(UploadValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "Invalid upload request",
                        ex.getCode(),
                        ex.getDetail()
                ));
    }

    @ExceptionHandler(AnalysisCallbackService.UploadNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadNotFound(AnalysisCallbackService.UploadNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        "Not Found",
                        ex.getCode(),
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(AnalysisCallbackService.UploadConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleUploadConflict(AnalysisCallbackService.UploadConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "Conflict",
                        ex.getCode(),
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "Invalid request payload",
                        "VALIDATION_ERROR",
                        detail.isBlank() ? "Validation failed" : detail
                ));
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "Invalid request",
                        "BAD_REQUEST",
                        ex.getMessage() == null ? "Request could not be processed" : ex.getMessage()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "Invalid upload request",
                        "FILE_SIZE_EXCEEDED",
                        "File size exceeds maximum allowed size of 1GB"
                ));
    }

    @ExceptionHandler({
            HttpMediaTypeNotSupportedException.class,
            HttpMessageNotReadableException.class,
            HttpMessageConversionException.class,
            MultipartException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMultipartParsing(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "Invalid upload request",
                        "INVALID_MULTIPART_METADATA",
                        "The metadata part must be valid JSON with Content-Type application/json"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        System.err.println("===== UNEXPECTED EXCEPTION =====");
        System.err.println("Exception type: " + ex.getClass().getName());
        System.err.println("Message: " + ex.getMessage());
        ex.printStackTrace(System.err);
        System.err.println("===== END EXCEPTION =====");
        log.error("Unexpected error in request processing", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "Unexpected error",
                        "INTERNAL_ERROR",
                        "An unexpected error occurred while processing the request"
                ));
    }
}
