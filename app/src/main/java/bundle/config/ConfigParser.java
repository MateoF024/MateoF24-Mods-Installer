package bundle.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * ConfigParser simplificado para extraer modpacks y sus URLs de descarga directa.
 *
 * Formato JSON esperado:
 * {
 *   "modpacks": {
 *     "NombreModpack": "https://url-de-descarga.com/archivo.zip",
 *     "OtroModpack": "https://otra-url.com/archivo.zip"
 *   }
 * }
 */
public final class ConfigParser {

    private ConfigParser() { }

    public static InstallerConfig parse(JsonObject root) throws ConfigParseException {
        if (root == null) {
            throw new ConfigParseException("Config JSON is null");
        }

        // Buscar la sección "modpacks"
        if (!root.has("modpacks") || !root.get("modpacks").isJsonObject()) {
            throw new ConfigParseException("Missing or invalid 'modpacks' section in config JSON");
        }

        JsonObject modpacks = root.getAsJsonObject("modpacks");
        InstallerConfig.Builder builder = new InstallerConfig.Builder();

        // Procesar cada modpack
        for (Map.Entry<String, JsonElement> entry : modpacks.entrySet()) {
            String modpackName = entry.getKey();
            JsonElement urlElement = entry.getValue();

            // Validar que el valor sea una URL (string)
            if (!urlElement.isJsonPrimitive() || !urlElement.getAsJsonPrimitive().isString()) {
                System.err.println("Advertencia: Modpack '" + modpackName + "' no tiene una URL válida, se omitirá.");
                continue;
            }

            String downloadUrl = urlElement.getAsString();

            // Validar que la URL no esté vacía
            if (downloadUrl.trim().isEmpty()) {
                System.err.println("Advertencia: Modpack '" + modpackName + "' tiene una URL vacía, se omitirá.");
                continue;
            }

            // Crear DownloadConfig simplificado
            DownloadConfig downloadConfig = new DownloadConfig(modpackName, downloadUrl.trim());
            builder.with(modpackName, downloadConfig);
        }

        return builder.build();
    }
}