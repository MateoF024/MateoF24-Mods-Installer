package bundle.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

public final class ConfigParser {

    private ConfigParser() { }

    public static InstallerConfig parse(JsonObject root) throws ConfigParseException {
        if (root == null) {
            throw new ConfigParseException("Config JSON is null");
        }

        if (!root.has("modpacks") || !root.get("modpacks").isJsonObject()) {
            throw new ConfigParseException("Missing or invalid 'modpacks' section in config JSON");
        }

        JsonObject modpacks = root.getAsJsonObject("modpacks");
        InstallerConfig.Builder builder = new InstallerConfig.Builder();

        for (Map.Entry<String, JsonElement> entry : modpacks.entrySet()) {
            String modpackName = entry.getKey();
            JsonElement urlElement = entry.getValue();

            if (!urlElement.isJsonPrimitive() || !urlElement.getAsJsonPrimitive().isString()) {
                System.err.println("Advertencia: Modpack '" + modpackName + "' no tiene una URL válida, se omitirá.");
                continue;
            }

            String downloadUrl = urlElement.getAsString().trim();
            if (downloadUrl.isEmpty()) {
                System.err.println("Advertencia: Modpack '" + modpackName + "' tiene una URL vacía, se omitirá.");
                continue;
            }

            DownloadConfig downloadConfig = new DownloadConfig(modpackName, downloadUrl);
            builder.with(modpackName, downloadConfig);
        }

        return builder.build();
    }
}