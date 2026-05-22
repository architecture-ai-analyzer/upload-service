package com.fiap.hackathon.upload_service.adapter.controller;

import com.fiap.hackathon.upload_service.adapter.dto.ApiErrorResponse;
import com.fiap.hackathon.upload_service.service.AnalysisCallbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiExceptionHandlerTest {

    @InjectMocks
    private ApiExceptionHandler apiExceptionHandler;

    @Test
    void handleResourceNotFound_ShouldReturn404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("RESOURCE_NOT_FOUND", "Resource not found");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleResourceNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("RESOURCE_NOT_FOUND", body.code());
        assertEquals("Resource not found", body.message());
    }

    @Test
    void handleUploadValidation_ShouldReturn400() {
        UploadValidationException ex = new UploadValidationException("VALIDATION_ERROR", "Invalid upload");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleUploadValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("VALIDATION_ERROR", body.code());
        assertEquals("Invalid upload request", body.message());
    }

    @Test
    void handleUploadNotFound_ShouldReturn404() {
        AnalysisCallbackService.UploadNotFoundException ex = new AnalysisCallbackService.UploadNotFoundException("UPLOAD_NOT_FOUND", "Upload not found");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleUploadNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("UPLOAD_NOT_FOUND", body.code());
        assertEquals("Not Found", body.message());
    }

    @Test
    void handleUploadConflict_ShouldReturn409() {
        AnalysisCallbackService.UploadConflictException ex = new AnalysisCallbackService.UploadConflictException("UPLOAD_CONFLICT", "Upload conflict");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleUploadConflict(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("UPLOAD_CONFLICT", body.code());
        assertEquals("Conflict", body.message());
    }

    @Test
    void handleMethodArgumentNotValid_ShouldReturn400WithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "field", "default message");
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("VALIDATION_ERROR", body.code());
        assertEquals("Invalid request payload", body.message());
        assertTrue(body.detail().contains("field"));
    }

    @Test
    void handleBadRequest_WithMissingServletRequestPartException_ShouldReturn400() {
        MissingServletRequestPartException ex = new MissingServletRequestPartException("file");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.code());
        assertEquals("Invalid request", body.message());
    }

    @Test
    void handleBadRequest_WithMissingServletRequestParameterException_ShouldReturn400() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("param", "String");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.code());
        assertEquals("Invalid request", body.message());
    }

    @Test
    void handleBadRequest_WithMethodArgumentTypeMismatchException_ShouldReturn400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getMessage()).thenReturn("Type mismatch");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.code());
        assertEquals("Invalid request", body.message());
    }

    @Test
    void handleBadRequest_WithIllegalArgumentException_ShouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BAD_REQUEST", body.code());
        assertEquals("Invalid request", body.message());
    }

    @Test
    void handleMaxUploadSize_ShouldReturn400() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1024L);

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("FILE_SIZE_EXCEEDED", body.code());
        assertEquals("Invalid upload request", body.message());
        assertTrue(body.detail().contains("1GB"));
    }

    @Test
    void handleMultipartParsing_WithHttpMediaTypeNotSupportedException_ShouldReturn400() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException("application/json");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleMultipartParsing(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("INVALID_MULTIPART_METADATA", body.code());
        assertEquals("Invalid upload request", body.message());
    }

    @Test
    void handleMultipartParsing_WithHttpMessageNotReadableException_ShouldReturn400() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("Not readable");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleMultipartParsing(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("INVALID_MULTIPART_METADATA", body.code());
        assertEquals("Invalid upload request", body.message());
    }

    @Test
    void handleMultipartParsing_WithMultipartException_ShouldReturn400() {
        MultipartException ex = new MultipartException("Multipart error");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleMultipartParsing(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("INVALID_MULTIPART_METADATA", body.code());
        assertEquals("Invalid upload request", body.message());
    }

    @Test
    void handleUnexpected_ShouldReturn500() {
        Exception ex = new Exception("Unexpected error");

        ResponseEntity<ApiErrorResponse> response = apiExceptionHandler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.code());
        assertEquals("Unexpected error", body.message());
    }
}
