package bundle.download;

import bundle.config.DownloadConfig;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DownloadManager {

    private static final int CONNECT_TIMEOUT = 30_000;
    private static final int READ_TIMEOUT = 60_000;
    // REVERTIR A BUFFER ORIGINAL QUE FUNCIONABA
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB - el original que funcionaba bien

    private DownloadManager() { }

    public static List<DownloadException> downloadFilesTo(Path targetDir, DownloadConfig dlConfig) {
        return downloadFilesTo(targetDir, dlConfig, null);
    }

    public static List<DownloadException> downloadFilesTo(Path targetDir, DownloadConfig dlConfig, ProgressCallback progressCallback) {
        List<DownloadException> errors = new ArrayList<>();

        for (String url : dlConfig.urls) {
            try {
                downloadTo(targetDir, url, progressCallback);
            } catch (DownloadException | IOException e) {
                errors.add(new DownloadException("Descarga fallida desde: " + url, e));
            }
        }
        return errors;
    }

    private static Path downloadTo(Path targetDir, String urlString, ProgressCallback progressCallback)
            throws IOException, DownloadException {

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // CONFIGURACIÓN ORIGINAL QUE FUNCIONABA
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Connection", "keep-alive");

        try {
            int status = conn.getResponseCode();
            if (status >= 400) {
                throw new DownloadException("HTTP error " + status + " for url: " + urlString);
            }

            long contentLength = conn.getContentLengthLong();
            String fileName = extractFileName(conn, url);
            Path tempFile = Files.createTempFile(targetDir, "dl-", ".part");

            ProgressTracker progressTracker = null;
            if (progressCallback != null) {
                progressTracker = new ProgressTracker(progressCallback, fileName, contentLength);
                progressCallback.onDownloadStart(fileName, contentLength);
            }

            // REVERTIR A LA LÓGICA ORIGINAL DE TRANSFERENCIA QUE FUNCIONABA
            try (InputStream inputStream = conn.getInputStream();
                 ReadableByteChannel inputChannel = Channels.newChannel(inputStream);
                 WritableByteChannel outputChannel = Files.newByteChannel(tempFile,
                         StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                transferOriginalMethod(inputChannel, outputChannel, progressTracker);
            }

            Path finalPath = targetDir.resolve(fileName);
            Files.move(tempFile, finalPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            if (progressTracker != null) {
                progressTracker.complete();
            }

            return finalPath;

        } finally {
            conn.disconnect();
        }
    }

    // MÉTODO DE TRANSFERENCIA ORIGINAL QUE FUNCIONABA CORRECTAMENTE
    private static void transferOriginalMethod(ReadableByteChannel input, WritableByteChannel output,
                                               ProgressTracker progressTracker) throws IOException {

        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

        long totalBytesRead = 0;
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {
            totalBytesRead += bytesRead;
            buffer.flip();

            while (buffer.hasRemaining()) {
                output.write(buffer);
            }

            buffer.clear();

            // REPORTAR PROGRESO SIN THROTTLING AGRESIVO - CADA CHUNK COMO ANTES
            if (progressTracker != null) {
                progressTracker.updateProgress(bytesRead);
            }
        }
    }

    private static String extractFileName(HttpURLConnection conn, URL url) {
        String contentDisposition = conn.getHeaderField("Content-Disposition");
        if (contentDisposition != null) {
            String[] parts = contentDisposition.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("filename=")) {
                    String fileName = part.substring("filename=".length())
                            .replaceAll("^\"|\"$", "")
                            .trim();
                    if (!fileName.isEmpty()) {
                        return sanitizeFileName(fileName);
                    }
                }
            }
        }

        String urlPath = url.getPath();
        if (urlPath != null && !urlPath.isEmpty()) {
            String fileName = Path.of(urlPath).getFileName().toString();
            if (!fileName.isEmpty() && !fileName.equals("/")) {
                return sanitizeFileName(fileName);
            }
        }

        return "download-" + Instant.now().toEpochMilli() + ".zip";
    }

    private static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    // CLASE INTERNA SIMPLIFICADA PERO SIN THROTTLING AGRESIVO
    private static class ProgressTracker {
        private final ProgressCallback callback;
        private final String fileName;
        private final long totalBytes;
        private long bytesDownloaded = 0;
        private long startTime;
        private long lastUpdateTime;
        private double lastCalculatedSpeed = 0;

        public ProgressTracker(ProgressCallback callback, String fileName, long totalBytes) {
            this.callback = callback;
            this.fileName = fileName;
            this.totalBytes = totalBytes;
            this.startTime = System.currentTimeMillis();
            this.lastUpdateTime = startTime;
        }

        public void updateProgress(long additionalBytes) {
            bytesDownloaded += additionalBytes;
            long currentTime = System.currentTimeMillis();

            // THROTTLING MÁS SUAVE - ACTUALIZAR CADA 100ms EN LUGAR DE 200ms
            if (currentTime - lastUpdateTime >= 100) {
                // CÁLCULO DE VELOCIDAD CORREGIDO
                long totalTimeDiff = currentTime - startTime;

                if (totalTimeDiff > 0) {
                    double currentSpeed = (double) bytesDownloaded / (totalTimeDiff / 1000.0);

                    // SUAVIZADO MENOS AGRESIVO
                    if (lastCalculatedSpeed == 0) {
                        lastCalculatedSpeed = currentSpeed;
                    } else {
                        lastCalculatedSpeed = (lastCalculatedSpeed * 0.8) + (currentSpeed * 0.2);
                    }
                }

                if (callback != null) {
                    callback.onProgress(bytesDownloaded, totalBytes, lastCalculatedSpeed, fileName);
                }

                lastUpdateTime = currentTime;
            }
        }

        public void complete() {
            if (callback != null) {
                callback.onDownloadComplete(fileName);
            }
        }
    }

    // Métodos de utilidad para formateo
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public static String formatSpeed(double bytesPerSecond) {
        return formatBytes((long) bytesPerSecond) + "/s";
    }
}