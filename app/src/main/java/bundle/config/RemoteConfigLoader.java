package bundle.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class RemoteConfigLoader {

    private static final String GIST_ID = "50ece138114a907d053a2622ff900fa4";
    private static final String FILE_NAME = "installer_config.json";
    private static final String GITHUB_API_URL = "https://api.github.com/gists/" + GIST_ID;
    private static final String FALLBACK_RAW_URL =
            "https://gist.githubusercontent.com/MateoF024/50ece138114a907d053a2622ff900fa4/raw/installer_config.json";

    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int READ_TIMEOUT = 15_000;

    private RemoteConfigLoader() { }

    public static JsonObject loadRemoteConfig() {
        System.out.println("=== Cargando configuración remota ===");

        JsonObject config = loadFromGitHubAPI();
        if (config != null) {
            System.out.println("✓ Configuración cargada desde GitHub API");
            return config;
        }

        System.out.println("⚠ API falló, intentando con URL raw como fallback...");
        config = loadFromRawUrl(FALLBACK_RAW_URL);
        if (config != null) {
            System.out.println("✓ Configuración cargada desde URL raw");
            return config;
        }

        System.err.println("✗ No se pudo cargar la configuración remota");
        return null;
    }

    private static JsonObject loadFromGitHubAPI() {
        try {
            System.out.println("Consultando GitHub API: " + GITHUB_API_URL);

            URL url = new URL(GITHUB_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "ModpackInstaller/1.0");

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = connection.getInputStream();
                     InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

                    Gson gson = new Gson();
                    JsonObject gistResponse = gson.fromJson(reader, JsonObject.class);

                    if (gistResponse.has("files")) {
                        JsonObject files = gistResponse.getAsJsonObject("files");
                        if (files.has(FILE_NAME)) {
                            JsonObject fileInfo = files.getAsJsonObject(FILE_NAME);
                            if (fileInfo.has("content")) {
                                String content = fileInfo.get("content").getAsString();
                                return gson.fromJson(content, JsonObject.class);
                            }
                        }
                    }

                    System.err.println("✗ No se encontró el archivo '" + FILE_NAME + "' en el Gist");
                }
            } else {
                System.err.println("✗ Error HTTP en GitHub API: " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("✗ Error al consultar GitHub API: " + e.getMessage());
        }

        return null;
    }

    private static JsonObject loadFromRawUrl(String rawUrl) {
        try {
            System.out.println("Consultando URL raw: " + rawUrl);

            URL url = new URL(rawUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 ModpackInstaller/1.0");
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = connection.getInputStream();
                     InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

                    Gson gson = new Gson();
                    return gson.fromJson(reader, JsonObject.class);
                }
            } else {
                System.err.println("✗ Error HTTP en URL raw: " + responseCode);
            }

        } catch (Exception e) {
            System.err.println("✗ Error al cargar desde URL raw: " + e.getMessage());
        }

        return null;
    }

    public static boolean isValidConfig(JsonObject config) {
        if (config == null) {
            return false;
        }

        if (!config.has("modpacks") || !config.get("modpacks").isJsonObject()) {
            System.err.println("✗ Configuración remota inválida: falta sección 'modpacks'");
            return false;
        }

        JsonObject modpacks = config.getAsJsonObject("modpacks");
        if (modpacks.size() == 0) {
            System.err.println("✗ Configuración remota inválida: sección 'modpacks' está vacía");
            return false;
        }

        System.out.println("✓ Configuración válida con " + modpacks.size() + " modpack(s)");
        return true;
    }

    public static JsonObject loadAndValidateRemoteConfig() {
        JsonObject config = loadRemoteConfig();
        if (config != null && isValidConfig(config)) {
            return config;
        }
        return null;
    }
}