package bundle.download;

import bundle.config.DownloadConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static bundle.download.DirectDownload.downloadTo;

public class DownloadManager {

    /**
     * setup() Se mantuvo para compatibilidad con versiones anteriores de llamadas.
     */
    public static void setup() {
    };

    /**
     * Descarga todas las URL configuradas en targetDir. Para cada URL, intenta descargar y recopilar los errores.
     * Devuelve una lista de DownloadException para errores (vacía si todos tuvieron éxito).
     */
    public static List<DownloadException> downloadFilesTo(Path targetDir, DownloadConfig dlConfig) {
        return downloadFilesTo(targetDir, dlConfig, null);
    }

    /**
     * Descarga todas las URL configuradas en targetDir con callback de progreso.
     * Para cada URL, intenta descargar y recopilar los errores.
     * Devuelve una lista de DownloadException para errores (vacía si todos tuvieron éxito).
     *
     * @param targetDir Directorio de destino
     * @param dlConfig Configuración de descarga
     * @param progressCallback Callback para reportar progreso (puede ser null)
     */
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
}