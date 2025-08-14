package bundle.download;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DirectDownload - Versión conservadora que mantiene la funcionalidad original
 * con solo el añadido de callbacks de progreso
 */
public final class DirectDownload {

    // Pool de threads para descargas asíncronas
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "download-thread");
        t.setDaemon(true);
        return t;
    });

    // Buffers conservadores - volvemos a los valores originales
    private static final int LARGE_BUFFER_SIZE = 1024 * 1024; // 1MB como original
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;  // 64KB como original

    private DirectDownload() { }

    /**
     * Descarga síncrona sin callback de progreso
     */
    public static Path downloadTo(Path targetDir, String urlString) throws IOException, DownloadException {
        return downloadToInternal(targetDir, urlString, null);
    }

    /**
     * Descarga síncrona con callback de progreso
     */
    public static Path downloadTo(Path targetDir, String urlString, ProgressCallback progressCallback)
            throws IOException, DownloadException {
        return downloadToInternal(targetDir, urlString, progressCallback);
    }

    /**
     * Descarga asíncrona - útil para no bloquear la UI
     */
    public static CompletableFuture<Path> downloadToAsync(Path targetDir, String urlString) {
        return downloadToAsync(targetDir, urlString, null);
    }

    /**
     * Descarga asíncrona con callback de progreso
     */
    public static CompletableFuture<Path> downloadToAsync(Path targetDir, String urlString, ProgressCallback progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return downloadToInternal(targetDir, urlString, progressCallback);
            } catch (IOException | DownloadException e) {
                throw new RuntimeException(e);
            }
        }, EXECUTOR);
    }

    private static Path downloadToInternal(Path targetDir, String urlString, ProgressCallback progressCallback)
            throws IOException, DownloadException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Configuración ORIGINAL conservadora - no tocar lo que funcionaba
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);
        conn.setInstanceFollowRedirects(true);

        // Headers ORIGINALES - solo los básicos que funcionaban
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Connection", "keep-alive");

        try {
            int status = conn.getResponseCode();
            if (status >= 400) {
                throw new DownloadException("HTTP error " + status + " for url: " + urlString);
            }

            // Obtener tamaño del archivo
            long contentLength = conn.getContentLengthLong();
            boolean isLargeFile = contentLength > 10 * 1024 * 1024; // > 10MB

            String fileName = extractFileName(conn, url);
            Path tempFile = Files.createTempFile(targetDir, "dl-", ".part");

            // Inicializar tracker de progreso si se proporciona callback
            DownloadProgressTracker progressTracker = null;
            if (progressCallback != null) {
                progressTracker = new DownloadProgressTracker(progressCallback, fileName, contentLength);
                progressCallback.onDownloadStart(fileName, contentLength);
            }

            // Usar la lógica de transferencia ORIGINAL con solo el añadido de progreso
            try (InputStream inputStream = conn.getInputStream();
                 ReadableByteChannel inputChannel = Channels.newChannel(inputStream);
                 WritableByteChannel outputChannel = Files.newByteChannel(tempFile,
                         StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                transferWithNIO(inputChannel, outputChannel, isLargeFile, contentLength, progressTracker);
            }

            Path finalPath = targetDir.resolve(fileName);
            Files.move(tempFile, finalPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            // Notificar completación
            if (progressTracker != null) {
                progressTracker.complete();
            }

            return finalPath;

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Transferencia con la lógica ORIGINAL, solo agregando reporte de progreso
     */
    private static void transferWithNIO(ReadableByteChannel input, WritableByteChannel output,
                                        boolean isLargeFile, long contentLength,
                                        DownloadProgressTracker progressTracker) throws IOException {

        // Usar buffers ORIGINALES
        int bufferSize = isLargeFile ? LARGE_BUFFER_SIZE : DEFAULT_BUFFER_SIZE;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);

        long totalBytesRead = 0;
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {
            totalBytesRead += bytesRead;
            buffer.flip();

            while (buffer.hasRemaining()) {
                output.write(buffer);
            }

            buffer.clear();

            // SOLO AGREGAR: Reportar progreso si hay tracker
            if (progressTracker != null) {
                progressTracker.updateProgress(bytesRead);
            }
        }
    }

    /**
     * Extracción del nombre de archivo - ORIGINAL sin cambios
     */
    private static String extractFileName(HttpURLConnection conn, URL url) {
        // Primero intentar Content-Disposition header
        String contentDisposition = conn.getHeaderField("Content-Disposition");
        if (contentDisposition != null) {
            String[] parts = contentDisposition.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("filename=")) {
                    String fileName = part.substring("filename=".length())
                            .replaceAll("^\"|\"$", "") // Remover comillas
                            .trim();
                    if (!fileName.isEmpty()) {
                        return sanitizeFileName(fileName);
                    }
                }
            }
        }

        // Luego intentar extraer de la URL
        String urlPath = url.getPath();
        if (urlPath != null && !urlPath.isEmpty()) {
            String fileName = Path.of(urlPath).getFileName().toString();
            if (!fileName.isEmpty() && !fileName.equals("/")) {
                return sanitizeFileName(fileName);
            }
        }

        // Fallback: usar timestamp con extensión .zip
        return "download-" + Instant.now().toEpochMilli() + ".zip";
    }

    /**
     * Sanitiza el nombre de archivo - ORIGINAL
     */
    private static String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /**
     * Cierra el pool de threads - ORIGINAL
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}