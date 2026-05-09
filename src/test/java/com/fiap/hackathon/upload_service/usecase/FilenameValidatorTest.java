package com.fiap.hackathon.upload_service.usecase;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class FilenameValidatorTest {
    
    private final FilenameValidator validator = new FilenameValidator();
    
    @Test
    public void testValidFilenames() {
        assertThat(validator.isValid("document.pdf")).isTrue();
        assertThat(validator.isValid("my-file_2025.pdf")).isTrue();
        assertThat(validator.isValid("report-v1.2.3.pdf")).isTrue();
        assertThat(validator.isValid("scan123.png")).isTrue();
        assertThat(validator.isValid("IMG_001.jpeg")).isTrue();
        assertThat(validator.isValid("file-name_123.jpg")).isTrue();
        assertThat(validator.isValid("a.pdf")).isTrue();
        assertThat(validator.isValid("123.pdf")).isTrue();
    }
    
    @Test
    public void testPathTraversalRejection() {
        assertThat(validator.isValid("../../../etc/passwd")).isFalse();
        assertThat(validator.isValid("..\\windows\\system32")).isFalse();
        assertThat(validator.isValid("file..pdf")).isFalse();
        assertThat(validator.validateAndGetError("../file.pdf"))
            .contains("path traversal");
    }
    
    @Test
    public void testSpecialCharactersRejection() {
        assertThat(validator.isValid("file@name.pdf")).isFalse();
        assertThat(validator.isValid("file#name.pdf")).isFalse();
        assertThat(validator.isValid("file$name.pdf")).isFalse();
        assertThat(validator.isValid("file%name.pdf")).isFalse();
        assertThat(validator.isValid("file&name.pdf")).isFalse();
        assertThat(validator.isValid("file*name.pdf")).isFalse();
        assertThat(validator.isValid("file?name.pdf")).isFalse();
        assertThat(validator.isValid("file<name.pdf")).isFalse();
        assertThat(validator.isValid("file>name.pdf")).isFalse();
        assertThat(validator.isValid("file|name.pdf")).isFalse();
        assertThat(validator.isValid("file:name.pdf")).isFalse();
        assertThat(validator.isValid("file;name.pdf")).isFalse();
    }
    
    @Test
    public void testDirectorySeparatorRejection() {
        assertThat(validator.isValid("dir/file.pdf")).isFalse();
        assertThat(validator.isValid("dir\\file.pdf")).isFalse();
        assertThat(validator.isValid("/file.pdf")).isFalse();
    }
    
    @Test
    public void testCommandInjectionRejection() {
        assertThat(validator.isValid("file$(whoami).pdf")).isFalse();
        assertThat(validator.isValid("file`whoami`.pdf")).isFalse();
        assertThat(validator.isValid("file;rm -rf.pdf")).isFalse();
        assertThat(validator.isValid("file&whoami.pdf")).isFalse();
        assertThat(validator.isValid("file|cat.pdf")).isFalse();
    }
    
    @Test
    public void testNullAndEmptyRejection() {
        assertThat(validator.isValid(null)).isFalse();
        assertThat(validator.isValid("")).isFalse();
        assertThat(validator.validateAndGetError(null))
            .contains("null");
        assertThat(validator.validateAndGetError(""))
            .contains("empty");
    }
    
    @Test
    public void testMaxLengthRejection() {
        String longFilename = "a".repeat(256) + ".pdf";
        assertThat(validator.isValid(longFilename)).isFalse();
        assertThat(validator.validateAndGetError(longFilename))
            .contains("exceeds maximum length");
    }
    
    @Test
    public void testNullByteRejection() {
        assertThat(validator.isValid("file\0.pdf")).isFalse();
        assertThat(validator.isValid("file%00.pdf")).isFalse();
    }
    
    @Test
    public void testHomeDirectoryRejection() {
        assertThat(validator.isValid("~/file.pdf")).isFalse();
    }
    
    @Test
    public void testQuotesRejection() {
        assertThat(validator.isValid("file\"name.pdf")).isFalse();
        assertThat(validator.isValid("file'name.pdf")).isFalse();
        assertThat(validator.isValid("file`name.pdf")).isFalse();
    }
    
    @Test
    public void testBracketsRejection() {
        assertThat(validator.isValid("file[name].pdf")).isFalse();
        assertThat(validator.isValid("file{name}.pdf")).isFalse();
        assertThat(validator.isValid("file(name).pdf")).isFalse();
    }
    
    @Test
    public void testSanitizeMethod() {
        assertThat(validator.sanitize("file@#$%.pdf"))
            .isEqualTo("file____.pdf");
        assertThat(validator.sanitize("file/name.pdf"))
            .isEqualTo("file_name.pdf");
        assertThat(validator.sanitize("file name.pdf"))
            .isEqualTo("file_name.pdf");
    }
    
    @Test
    public void testValidateAndGetErrorMessages() {
        assertThat(validator.validateAndGetError("file/name.pdf"))
            .contains("invalid character");
        assertThat(validator.validateAndGetError("file@name.pdf"))
            .contains("invalid character");
        assertThat(validator.validateAndGetError("a".repeat(300) + ".pdf"))
            .contains("maximum length");
    }
}
