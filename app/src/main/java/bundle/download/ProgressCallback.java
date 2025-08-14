package bundle.download;

/**
 * Interfaz para reportar progreso de descarga con métricas en tiempo real
 */
public interface ProgressCallback {
    /**
     * Llamado cuando el progreso de descarga cambia
     *
     * @param bytesDownloaded Bytes descargados hasta ahora
     * @param totalBytes Total de bytes a descargar (-1 si es desconocido)
     * @param downloadSpeed Velocidad de descarga en bytes por segundo
     * @param fileName Nombre del archivo siendo descargado
     */
    void onProgress(long bytesDownloaded, long totalBytes, double downloadSpeed, String fileName);

    /**
     * Llamado cuando una descarga se completa
     *
     * @param fileName Nombre del archivo completado
     */
    void onDownloadComplete(String fileName);

    /**
     * Llamado cuando se inicia una nueva descarga
     *
     * @param fileName Nombre del archivo que se va a descargar
     * @param totalBytes Total de bytes a descargar (-1 si es desconocido)
     */
    void onDownloadStart(String fileName, long totalBytes);
}