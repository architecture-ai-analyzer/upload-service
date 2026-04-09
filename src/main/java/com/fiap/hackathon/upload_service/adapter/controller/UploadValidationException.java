package com.fiap.hackathon.upload_service.adapter.controller;

public class UploadValidationException extends RuntimeException {

    private final String code;
    private final String detail;

    public UploadValidationException(String code, String detail) {
        super(detail);
        this.code = code;
        this.detail = detail;
    }

    public String getCode() {
        return code;
    }

    public String getDetail() {
        return detail;
    }
}
