package com.fiap.hackathon.upload_service.usecase;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Component
public class FileSignatureValidator {

    public String detectContentType(MultipartFile file) throws IOException {
        byte[] header = readHeader(file, 16);
        if (isPdf(header)) {
            return "application/pdf";
        }
        if (isPng(header)) {
            return "image/png";
        }
        if (isJpeg(header)) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private byte[] readHeader(MultipartFile file, int maxBytes) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(maxBytes);
        }
    }

    private boolean isPdf(byte[] bytes) {
        if (bytes.length < 5) {
            return false;
        }
        return bytes[0] == 0x25
                && bytes[1] == 0x50
                && bytes[2] == 0x44
                && bytes[3] == 0x46
                && bytes[4] == 0x2D;
    }

    private boolean isPng(byte[] bytes) {
        if (bytes.length < 8) {
            return false;
        }
        return (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private boolean isJpeg(byte[] bytes) {
        if (bytes.length < 3) {
            return false;
        }
        return (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }
}
