package com.fiap.hackathon.upload_service.infra.scanner;

public class ScanResult {

    private final boolean infected;
    private final String signature;

    public ScanResult(boolean infected, String signature) {
        this.infected = infected;
        this.signature = signature;
    }

    public boolean isInfected() {
        return infected;
    }

    public String getSignature() {
        return signature;
    }
}
