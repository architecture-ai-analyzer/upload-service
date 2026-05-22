package com.fiap.hackathon.upload_service.usecase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FileSignatureValidatorTest {

    @InjectMocks
    private FileSignatureValidator fileSignatureValidator;

    @Test
    void detectContentType_WithPdfHeader_ShouldReturnPdf() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", pdfHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("application/pdf", contentType);
    }

    @Test
    void detectContentType_WithPngHeader_ShouldReturnPng() throws IOException {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", pngHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("image/png", contentType);
    }

    @Test
    void detectContentType_WithJpegHeader_ShouldReturnJpeg() throws IOException {
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", jpegHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("image/jpeg", contentType);
    }

    @Test
    void detectContentType_WithUnknownHeader_ShouldReturnOctetStream() throws IOException {
        byte[] unknownHeader = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05};
        MultipartFile file = new MockMultipartFile("file", unknownHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("application/octet-stream", contentType);
    }

    @Test
    void detectContentType_WithEmptyFile_ShouldReturnOctetStream() throws IOException {
        byte[] emptyHeader = new byte[]{};
        MultipartFile file = new MockMultipartFile("file", emptyHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("application/octet-stream", contentType);
    }

    @Test
    void detectContentType_WithShortPdfHeader_ShouldReturnOctetStream() throws IOException {
        byte[] shortHeader = new byte[]{0x25, 0x50, 0x44, 0x46};
        MultipartFile file = new MockMultipartFile("file", shortHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("application/octet-stream", contentType);
    }

    @Test
    void detectContentType_WithShortPngHeader_ShouldReturnOctetStream() throws IOException {
        byte[] shortHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A};
        MultipartFile file = new MockMultipartFile("file", shortHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("application/octet-stream", contentType);
    }

    @Test
    void detectContentType_WithShortJpegHeader_ShouldReturnOctetStream() throws IOException {
        byte[] shortHeader = new byte[]{(byte) 0xFF, (byte) 0xD8};
        MultipartFile file = new MockMultipartFile("file", shortHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("application/octet-stream", contentType);
    }

    @Test
    void detectContentType_WithPdfHeaderExactLength_ShouldReturnPdf() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};
        MultipartFile file = new MockMultipartFile("file", pdfHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("application/pdf", contentType);
    }

    @Test
    void detectContentType_WithPngHeaderExactLength_ShouldReturnPng() throws IOException {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MultipartFile file = new MockMultipartFile("file", pngHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("image/png", contentType);
    }

    @Test
    void detectContentType_WithJpegHeaderExactLength_ShouldReturnJpeg() throws IOException {
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        MultipartFile file = new MockMultipartFile("file", jpegHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("image/jpeg", contentType);
    }

    @Test
    void detectContentType_WithPdfHeaderLongerThanRequired_ShouldReturnPdf() throws IOException {
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x00, 0x00, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", pdfHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("application/pdf", contentType);
    }

    @Test
    void detectContentType_WithPngHeaderLongerThanRequired_ShouldReturnPng() throws IOException {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", pngHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("image/png", contentType);
    }

    @Test
    void detectContentType_WithJpegHeaderLongerThanRequired_ShouldReturnJpeg() throws IOException {
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", jpegHeader);

        String contentType = fileSignatureValidator.detectContentType(file);

        assertEquals("image/jpeg", contentType);
    }
}
