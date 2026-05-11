package com.fiap.hackathon.upload_service.usecase;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Validates and sanitizes filenames to prevent path traversal and injection attacks.
 * 
 * Allowed characters: alphanumeric, dash, underscore, dot
 * Pattern: [a-zA-Z0-9._-]+
 * Max length: 255 characters
 */
@Component
public class FilenameValidator {
    
    private static final Pattern VALID_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final String PATH_TRAVERSAL_PATTERN = "\\.\\.";
    private static final String[] DANGEROUS_PATTERNS = {
            "..",          // parent directory reference
            "~",           // home directory reference
            "\0",          // null byte
            "%00",         // null byte encoded
            "\n",          // newline
            "\r",          // carriage return
            "/",           // directory separator
            "\\",          // backslash separator
            "|",           // pipe
            "<",           // redirection
            ">",           // redirection
            "*",           // wildcard
            "?",           // wildcard
            ":",           // drive letter (Windows)
            "\"",          // quote
            "'",           // quote
            "`",           // backtick
            "$",           // variable
            "&",           // ampersand
            ";",           // command separator
            "(",           // command injection
            ")",           // command injection
            "{",           // expansion
            "}",           // expansion
            "[",           // expansion
            "]",           // expansion
            "^",           // special char
            "!",           // history expansion
    };
    
    /**
     * Validates if a filename is safe.
     * 
     * @param filename the filename to validate
     * @return true if filename is safe, false otherwise
     */
    public boolean isValid(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        
        if (filename.length() > MAX_FILENAME_LENGTH) {
            return false;
        }
        
        // Check for path traversal
        if (filename.contains("..")) {
            return false;
        }
        
        // Check for dangerous patterns
        for (String pattern : DANGEROUS_PATTERNS) {
            if (filename.contains(pattern)) {
                return false;
            }
        }
        
        // Check valid pattern: only alphanumeric, dot, dash, underscore
        return VALID_FILENAME_PATTERN.matcher(filename).matches();
    }
    
    /**
     * Validates filename and returns detailed error message.
     * 
     * @param filename the filename to validate
     * @return null if valid, error message otherwise
     */
    public String validateAndGetError(String filename) {
        if (filename == null) {
            return "Filename cannot be null";
        }
        
        if (filename.isEmpty()) {
            return "Filename cannot be empty";
        }
        
        if (filename.length() > MAX_FILENAME_LENGTH) {
            return "Filename exceeds maximum length of " + MAX_FILENAME_LENGTH + " characters";
        }
        
        // Check for path traversal
        if (filename.contains("..")) {
            return "Filename contains path traversal pattern (..)";
        }
        
        // Check for dangerous patterns
        for (String pattern : DANGEROUS_PATTERNS) {
            if (filename.contains(pattern)) {
                return "Filename contains invalid character: " + escapeForDisplay(pattern);
            }
        }
        
        // Check valid pattern
        if (!VALID_FILENAME_PATTERN.matcher(filename).matches()) {
            return "Filename contains invalid characters. Only alphanumeric, dot (.), dash (-), and underscore (_) are allowed";
        }
        
        return null;
    }
    
    /**
     * Escapes special characters for display in error messages.
     */
    private String escapeForDisplay(String pattern) {
        return switch (pattern) {
            case "\n" -> "\\n (newline)";
            case "\r" -> "\\r (carriage return)";
            case "\0" -> "\\0 (null)";
            case "\\" -> "\\\\ (backslash)";
            default -> "'" + pattern + "'";
        };
    }
    
    /**
     * Sanitizes a filename by removing invalid characters.
     * WARNING: This is for display purposes only. Validation should be done separately.
     * 
     * @param filename the filename to sanitize
     * @return sanitized filename
     */
    public String sanitize(String filename) {
        if (filename == null) {
            return "";
        }
        
        // Remove all non-safe characters
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
