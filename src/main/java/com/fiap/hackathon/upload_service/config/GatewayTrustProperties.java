package com.fiap.hackathon.upload_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.gateway-trust")
public class GatewayTrustProperties {

    private boolean enabled = false;
    private String userHeader = "X-Authenticated-User";
    private String scopesHeader = "X-Authenticated-Scopes";
    private String signatureHeader = "X-Gateway-Signature";
    private String timestampHeader = "X-Gateway-Timestamp";
    private String sharedSecret = "";
    private long maxSkewSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUserHeader() {
        return userHeader;
    }

    public void setUserHeader(String userHeader) {
        this.userHeader = userHeader;
    }

    public String getScopesHeader() {
        return scopesHeader;
    }

    public void setScopesHeader(String scopesHeader) {
        this.scopesHeader = scopesHeader;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public String getTimestampHeader() {
        return timestampHeader;
    }

    public void setTimestampHeader(String timestampHeader) {
        this.timestampHeader = timestampHeader;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public long getMaxSkewSeconds() {
        return maxSkewSeconds;
    }

    public void setMaxSkewSeconds(long maxSkewSeconds) {
        this.maxSkewSeconds = maxSkewSeconds;
    }
}
