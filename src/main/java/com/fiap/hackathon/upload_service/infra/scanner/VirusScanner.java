package com.fiap.hackathon.upload_service.infra.scanner;

import java.nio.file.Path;

public interface VirusScanner {

    ScanResult scan(Path file) throws Exception;

}
