package bundle.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Clase para cargar configuración de modpacks desde GitHub Gist usando la API.
 *
 * Usa la GitHub API en lugar de URLs raw para evitar problemas de caché
 * y obtener siempre la versión más reciente del archivo.
 */
public final class RemoteConfigLoader {

    // ID del Gist (extraído de tu URL)
    private static final String GIST_ID = "50ece138114a907d053a2622ff900fa4";
    private static final String FILE_NAME = "installer_config.json";

    // URL de la GitHub API para obtener el Gist
    private static final String GITHUB_API_URL = "https://api.github.com/gists/" + GIST_ID;

    // URLs de fallback (mantener para compatibilidad)
    private static final String FALLBACK_RAW_URL =
            "https://gist.githubusercontent.com/MateoF024/50ece138114a907d053a2622ff900fa4/raw/installer_config.json";

    // Timeouts para la conexión remota
    private static final int CONNECT_TIMEOUT = 10_000; // 10 segundos
    private static final int READ_TIMEOUT = 15_000;    // 15 segundos

    private RemoteConfigLoader() {
        // Utility class - no instantiation
    }

    /**
     * Carga la configuración desde GitHub API (siempre la versión más reciente).
     *
     * @return JsonObject con la configuración, o null si no se pudo cargar
     */
    public static JsonObject loadRemoteConfig() {
        System.out.println("=== Cargando configuración remota ===");

        // Intentar primero con GitHub API
        JsonObject config = loadFromGitHubAPI();
        if (config != null) {
            System.out.println("✓ Configuración cargada desde GitHub API");
            return config;
        }

        // Fallback a URL raw (con caché potencial)
        System.out.println("⚠ API falló, intentando con URL raw como fallback...");
        config = loadFromRawUrl(FALLBACK_RAW_URL);
        if (config != null) {
            System.out.println("✓ Configuración cargada desde URL raw (puede estar cacheada)");
            return config;
        }

        System.err.println("✗ No se pudo cargar la configuración remota");
        return null;
    }

    /**
     * Carga la configuración usando la GitHub API.
     * Esto siempre retorna la versión más reciente sin problemas de caché.
     */
    private static JsonObject loadFromGitHubAPI() {
        try {
            System.out.println("Consultando GitHub API: " + GITHUB_API_URL);

            URL url = new URL(GITHUB_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Configurar la conexión para GitHub API
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);

            // Headers específicos para GitHub API
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "ModpackInstaller/1.0");
            // Si tienes un token de GitHub, descomenta la siguiente línea:
            // connection.setRequestProperty("Authorization", "Bearer YOUR_GITHUB_TOKEN");

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = connection.getInputStream();
                     InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

                    Gson gson = new Gson();
                    JsonObject gistResponse = gson.fromJson(reader, JsonObject.class);

                    // Extraer el contenido del archivo del Gist
                    if (gistResponse.has("files")) {
                        JsonObject files = gistResponse.getAsJsonObject("files");
                        if (files.has(FILE_NAME)) {
                            JsonObject fileInfo = files.getAsJsonObject(FILE_NAME);
                            if (fileInfo.has("content")) {
                                String content = fileInfo.get("content").getAsString();

                                // Parsear el contenido JSON
                                JsonObject configObject = gson.fromJson(content, JsonObject.class);

                                // Información adicional para debugging
                                if (gistResponse.has("updated_at")) {
                                    System.out.println("✓ Última actualización del Gist: " +
                                            gistResponse.get("updated_at").getAsString());
                                }

                                return configObject;
                            }
                        }
                    }

                    System.err.println("✗ No se encontró el archivo '" + FILE_NAME + "' en el Gist");
                }
            } else {
                System.err.println("✗ Error HTTP en GitHub API: " + responseCode + " " +
                        connection.getResponseMessage());

                // Imprimir detalles del error si están disponibles
                try (InputStream errorStream = connection.getErrorStream()) {
                    if (errorStream != null) {
                        String errorResponse = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
                        System.err.println("Respuesta de error de GitHub API: " + errorResponse);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("✗ Error al consultar GitHub API: " + e.getMessage());
        }

        return null;
    }

    /**
     * Método de fallback usando URL raw (puede tener caché).
     */
    private static JsonObject loadFromRawUrl(String rawUrl) {
        try {
            // Aplicar cache-busting agresivo
            String urlWithCacheBusting = applyCacheBusting(rawUrl);
            System.out.println("Consultando URL raw: " + rawUrl);

            URL url = new URL(urlWithCacheBusting);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Configurar la conexión
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);

            // Headers anti-caché muy agresivos
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ModpackInstaller/1.0");
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Expires", "0");
            connection.setRequestProperty("If-None-Match", "");
            connection.setRequestProperty("If-Modified-Since", "");

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

    /**
     * Aplica cache-busting muy agresivo.
     */
    private static String applyCacheBusting(String originalUrl) {
        String separator = originalUrl.contains("?") ? "&" : "?";
        long timestamp = System.currentTimeMillis();
        return originalUrl + separator + "nocache=" + timestamp + "&t=" + System.nanoTime();
    }

    /**
     * Carga la configuración de forma asíncrona.
     */
    public static CompletableFuture<JsonObject> loadRemoteConfigAsync() {
        return CompletableFuture.supplyAsync(() -> loadRemoteConfig())
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    System.err.println("✗ Timeout o error en carga asíncrona: " + throwable.getMessage());
                    return null;
                });
    }

    /**
     * Valida la estructura del JSON de configuración.
     */
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

    /**
     * Carga y valida la configuración remota.
     */
    public static JsonObject loadAndValidateRemoteConfig() {
        JsonObject config = loadRemoteConfig();
        if (config != null && isValidConfig(config)) {
            return config;
        }
        return null;
    }

    /**
     * Método para debugging - muestra información detallada.
     */
    public static void printConfigInfo() {
        System.out.println("=== Información de Configuración Remota ===");
        System.out.println("Gist ID: " + GIST_ID);
        System.out.println("Archivo: " + FILE_NAME);
        System.out.println("GitHub API URL: " + GITHUB_API_URL);
        System.out.println("Fallback Raw URL: " + FALLBACK_RAW_URL);

        JsonObject config = loadRemoteConfig();
        if (config != null) {
            System.out.println("Estado: ✓ Configuración remota accesible");
            if (config.has("version")) {
                System.out.println("Versión: " + config.get("version").getAsString());
            }
            if (config.has("modpacks")) {
                JsonObject modpacks = config.getAsJsonObject("modpacks");
                System.out.println("Modpacks disponibles: " + modpacks.keySet());
            }
        } else {
            System.out.println("Estado: ✗ Configuración remota no accesible");
        }
    }
}