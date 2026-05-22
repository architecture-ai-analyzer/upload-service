package com.fiap.hackathon.upload_service.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorsPropertiesTest {

    @Test
    void defaultConstructor_ShouldSetDefaultOrigins() {
        CorsProperties properties = new CorsProperties();

        List<String> origins = properties.getAllowedOrigins();
        assertNotNull(origins);
        assertTrue(origins.contains("http://localhost:5173"));
        assertTrue(origins.contains("http://localhost:5174"));
        assertTrue(origins.contains("https://*.cloudfront.net"));
    }

    @Test
    void defaultConstructor_ShouldSetDefaultMethods() {
        CorsProperties properties = new CorsProperties();

        List<String> methods = properties.getAllowedMethods();
        assertNotNull(methods);
        assertEquals(6, methods.size());
        assertTrue(methods.contains("GET"));
        assertTrue(methods.contains("POST"));
        assertTrue(methods.contains("PUT"));
        assertTrue(methods.contains("PATCH"));
        assertTrue(methods.contains("DELETE"));
        assertTrue(methods.contains("OPTIONS"));
    }

    @Test
    void defaultConstructor_ShouldSetDefaultHeaders() {
        CorsProperties properties = new CorsProperties();

        List<String> headers = properties.getAllowedHeaders();
        assertNotNull(headers);
        assertEquals(1, headers.size());
        assertEquals("*", headers.get(0));
    }

    @Test
    void defaultConstructor_ShouldSetDefaultAllowCredentials() {
        CorsProperties properties = new CorsProperties();

        assertTrue(properties.isAllowCredentials());
    }

    @Test
    void defaultConstructor_ShouldSetDefaultMaxAge() {
        CorsProperties properties = new CorsProperties();

        assertEquals(3600, properties.getMaxAge());
    }

    @Test
    void setAllowedOrigins_WithValidList_ShouldSetOrigins() {
        CorsProperties properties = new CorsProperties();
        List<String> newOrigins = List.of("https://example.com", "https://app.example.com");

        properties.setAllowedOrigins(newOrigins);

        assertEquals(newOrigins, properties.getAllowedOrigins());
    }

    @Test
    void setAllowedOrigins_WithNull_ShouldSetEmptyList() {
        CorsProperties properties = new CorsProperties();

        properties.setAllowedOrigins(null);

        assertNotNull(properties.getAllowedOrigins());
        assertTrue(properties.getAllowedOrigins().isEmpty());
    }

    @Test
    void setAllowedMethods_ShouldSetMethods() {
        CorsProperties properties = new CorsProperties();
        List<String> newMethods = List.of("GET", "POST");

        properties.setAllowedMethods(newMethods);

        assertEquals(newMethods, properties.getAllowedMethods());
    }

    @Test
    void setAllowedHeaders_ShouldSetHeaders() {
        CorsProperties properties = new CorsProperties();
        List<String> newHeaders = List.of("Content-Type", "Authorization");

        properties.setAllowedHeaders(newHeaders);

        assertEquals(newHeaders, properties.getAllowedHeaders());
    }

    @Test
    void setAllowCredentials_ShouldSetAllowCredentials() {
        CorsProperties properties = new CorsProperties();

        properties.setAllowCredentials(false);

        assertFalse(properties.isAllowCredentials());
    }

    @Test
    void setMaxAge_ShouldSetMaxAge() {
        CorsProperties properties = new CorsProperties();

        properties.setMaxAge(7200);

        assertEquals(7200, properties.getMaxAge());
    }
}
