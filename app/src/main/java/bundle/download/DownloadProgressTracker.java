package bundle.download;

/**
 * Clase para trackear el progreso de descarga con cálculo de velocidad
 */
public class DownloadProgressTracker {
    private final ProgressCallback callback;
    private final String fileName;
    private final long totalBytes;

    private long bytesDownloaded = 0;
    private long startTime;
    private long lastUpdateTime;
    private long lastBytesDownloaded = 0;

    // Para suavizar el cálculo de velocidad
    private static final int SPEED_CALCULATION_WINDOW_MS = 1000; // 1 segundo
    private double lastCalculatedSpeed = 0;

    public DownloadProgressTracker(ProgressCallback callback, String fileName, long totalBytes) {
        this.callback = callback;
        this.fileName = fileName;
        this.totalBytes = totalBytes;
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = startTime;
    }

    /**
     * Actualiza el progreso de descarga
     *
     * @param additionalBytes Bytes adicionales descargados desde la última actualización
     */
    public void updateProgress(long additionalBytes) {
        bytesDownloaded += additionalBytes;
        long currentTime = System.currentTimeMillis();

        // Reportar progreso solo cada 500ms para evitar saturar la UI
        if (currentTime - lastUpdateTime >= 500) {
            double downloadSpeed = calculateSpeed(currentTime);

            if (callback != null) {
                callback.onProgress(bytesDownloaded, totalBytes, downloadSpeed, fileName);
            }

            lastUpdateTime = currentTime;
        }
    }

    /**
     * Calcula la velocidad de descarga con suavizado
     */
    private double calculateSpeed(long currentTime) {
        long timeDiff = currentTime - lastUpdateTime;

        if (timeDiff < 100) { // Evitar cálculos muy frecuentes
            return lastCalculatedSpeed;
        }

        // Calcular velocidad basada en el tiempo total transcurrido
        long totalTimeDiff = currentTime - startTime;
        if (totalTimeDiff > 0) {
            double currentSpeed = (double) bytesDownloaded / (totalTimeDiff / 1000.0);

            // Suavizar la velocidad usando promedio ponderado
            if (lastCalculatedSpeed == 0) {
                lastCalculatedSpeed = currentSpeed;
            } else {
                lastCalculatedSpeed = (lastCalculatedSpeed * 0.7) + (currentSpeed * 0.3);
            }
        }

        return lastCalculatedSpeed;
    }

    /**
     * Marca la descarga como completada
     */
    public void complete() {
        if (callback != null) {
            callback.onDownloadComplete(fileName);
        }
    }

    /**
     * Convierte bytes a string legible
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Convierte velocidad de bytes/segundo a string legible
     */
    public static String formatSpeed(double bytesPerSecond) {
        return formatBytes((long) bytesPerSecond) + "/s";
    }
}