package com.fiap.hackathon.upload_service.infra.scanner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ClamdScanner implements VirusScanner {

    @Value("${application.clamd.host:localhost}")
    private String host;

    @Value("${application.clamd.port:3310}")
    private int port;

    private static final int CHUNK_SIZE = 2048;

    @Override
    public ScanResult scan(Path file) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(60_000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            InputStream in = socket.getInputStream();

            // send INSTREAM command
            out.write("INSTREAM\n".getBytes());

            try (InputStream fis = new BufferedInputStream(Files.newInputStream(file))) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    byte[] len = ByteBuffer.allocate(4).putInt(read).array();
                    out.write(len);
                    out.write(buffer, 0, read);
                }
            }

            // send zero-length chunk
            out.write(new byte[] {0,0,0,0});
            out.flush();

            // read response
            byte[] respBuf = new byte[4096];
            int r = in.read(respBuf);
            String resp = r > 0 ? new String(respBuf, 0, r) : "";
            // Example response: stream: Eicar-Test-Signature FOUND
            if (resp.contains("FOUND")) {
                String sig = resp.replaceAll("\r|\n", "").trim();
                return new ScanResult(true, sig);
            }
            return new ScanResult(false, "OK");
        }
    }
}
