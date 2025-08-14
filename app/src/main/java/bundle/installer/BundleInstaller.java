package bundle.installer;

import bundle.App;
import bundle.config.ConfigParseException;
import bundle.config.ConfigParser;
import bundle.config.DownloadConfig;
import bundle.config.InstallerConfig;
import bundle.config.RemoteConfigLoader;
import bundle.download.DownloadException;
import bundle.download.DownloadManager;
import bundle.download.ProgressCallback;
import bundle.gui.BundleGuiApp;
import bundle.util.OperatingSystem;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BundleInstaller {
    public Path gameDir;
    public String selectedInstall = "";
    public final InstallerConfig installerConfig;
    public final Properties installerProperties;
    public final BundleGuiApp gui;

    // Pool de threads optimizado
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.min(4, Runtime.getRuntime().availableProcessors()));

    // Buffer optimizado para descompresión
    private static final int UNZIP_BUFFER_SIZE = 256 * 1024;

    public BundleInstaller() {
        JsonObject configObject = null;
        InstallerConfig cfg = null;

        System.out.println("=== Cargando configuración de modpacks ===");

        // Intentar cargar configuración remota primero
        try {
            System.out.println("Intentando cargar configuración desde GitHub API...");
            configObject = RemoteConfigLoader.loadAndValidateRemoteConfig();

            if (configObject != null) {
                System.out.println("✓ Usando configuración remota actualizada");
                cfg = ConfigParser.parse(configObject);
            }
        } catch (Exception e) {
            System.err.println("✗ Error al cargar configuración remota: " + e.getMessage());
        }

        // Fallback a configuración local
        if (cfg == null) {
            System.out.println("Cargando configuración local de respaldo...");
            InputStream configStream = BundleInstaller.class.getClassLoader().getResourceAsStream("installer_config.json");
            if (configStream != null) {
                try {
                    InputStreamReader reader = new InputStreamReader(configStream, StandardCharsets.UTF_8);
                    configObject = new Gson().fromJson(reader, JsonObject.class);
                    cfg = ConfigParser.parse(configObject);
                    System.out.println("✓ Usando configuración local");
                } catch (ConfigParseException e) {
                    System.err.println("✗ Error al parsear configuración local: " + e.getMessage());
                    cfg = new InstallerConfig.Builder().build();
                }
            } else {
                System.err.println("✗ No se encontró configuración local");
                cfg = new InstallerConfig.Builder().build();
            }
        }

        this.installerConfig = cfg;

        // Seleccionar el primer modpack disponible
        if (!installerConfig.configNames.isEmpty()) {
            selectedInstall = installerConfig.configNames.get(0);
            System.out.println("Modpacks disponibles: " + installerConfig.configNames);
            System.out.println("Modpack seleccionado por defecto: " + selectedInstall);
        } else {
            System.err.println("⚠ No hay modpacks disponibles en la configuración");
        }

        // Cargar properties de la aplicación
        InputStream propertiesStream = App.class.getClassLoader().getResourceAsStream("installer.properties");
        Properties properties = new Properties();
        try {
            properties.load(propertiesStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.installerProperties = properties;

        // Establecer directorio de juego por defecto
        this.gameDir = OperatingSystem.getCurrent().getMCDir();

        // Inicializar la interfaz gráfica
        this.gui = new BundleGuiApp(this);

        System.out.println("=== Inicialización completada ===");
    }

    public void openUI() {
        gui.open();
    }

    public void install() throws IOException, DownloadException {
        install(null);
    }

    public void install(ProgressCallback progressCallback) throws IOException, DownloadException {
        if (this.gameDir == null) {
            throw new DownloadException("El directorio seleccionado esta vacio!");
        }

        // Limpiar archivos parciales
        cleanupPartialFiles(gameDir);

        // Limpiar carpetas conflictivas
        deleteDirectoriesOptimized(gameDir);

        DownloadConfig dlConfig = this.installerConfig.configs.get(selectedInstall);
        if (dlConfig == null) {
            throw new IllegalStateException("No se encontró una configuración válida para la instalación seleccionada: " + selectedInstall);
        }

        if (!Files.exists(gameDir)) {
            throw new DownloadException(String.format("El directorio '%s' no existe!", gameDir));
        }

        // Descargar archivos
        List<DownloadException> errors = DownloadManager.downloadFilesTo(gameDir, dlConfig, progressCallback);
        if (!errors.isEmpty()) {
            for (DownloadException e : errors) {
                e.printStackTrace();
            }
            throw new IOException("Errores durante la descarga, no se puede continuar.");
        }

        // Procesar archivos ZIP
        processZipFiles(gameDir);
    }

    private void cleanupPartialFiles(Path directory) {
        try {
            if (Files.exists(directory) && Files.isDirectory(directory)) {
                Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (file.getFileName().toString().matches("dl-.*\\.part")) {
                            try {
                                Files.deleteIfExists(file);
                            } catch (IOException e) {
                                System.err.println("No se pudo borrar parcial: " + file + " -> " + e.getMessage());
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            System.err.println("Advertencia al limpiar parciales: " + e.getMessage());
        }
    }

    private void processZipFiles(Path directory) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.zip")) {
            for (Path zipFile : stream) {
                System.out.println("Descomprimiendo: " + zipFile.getFileName());
                unzipFileOptimized(zipFile, directory);
                Files.deleteIfExists(zipFile);
                System.out.println("Archivo ZIP eliminado: " + zipFile.getFileName());
            }
        }
    }

    private void unzipFileOptimized(Path zipFilePath, Path targetDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipFilePath.toFile(), StandardCharsets.UTF_8)) {

            // Crear directorios necesarios
            zipFile.stream()
                    .filter(ZipEntry::isDirectory)
                    .forEach(entry -> {
                        try {
                            Path entryPath = targetDir.resolve(entry.getName());
                            Files.createDirectories(entryPath);
                        } catch (IOException e) {
                            System.err.println("Error creando directorio: " + entry.getName() + " -> " + e.getMessage());
                        }
                    });

            // Extraer archivos
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .forEach(entry -> {
                        try {
                            extractFileEntry(zipFile, entry, targetDir);
                        } catch (IOException e) {
                            System.err.println("Error extrayendo: " + entry.getName() + " -> " + e.getMessage());
                        }
                    });
        }
    }

    private void extractFileEntry(ZipFile zipFile, ZipEntry entry, Path targetDir) throws IOException {
        String entryName = entry.getName();
        Path entryPath = targetDir.resolve(entryName);

        Files.createDirectories(entryPath.getParent());

        // Usar NIO channels para archivos grandes (> 1MB)
        if (entry.getSize() > 1024 * 1024) {
            extractLargeFile(zipFile, entry, entryPath);
        } else {
            extractSmallFile(zipFile, entry, entryPath);
        }
    }

    private void extractLargeFile(ZipFile zipFile, ZipEntry entry, Path targetPath) throws IOException {
        try (InputStream inputStream = zipFile.getInputStream(entry);
             ReadableByteChannel inputChannel = Channels.newChannel(inputStream);
             FileChannel outputChannel = FileChannel.open(targetPath,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            long transferred = 0;
            long size = entry.getSize();

            while (transferred < size) {
                long count = outputChannel.transferFrom(inputChannel, transferred, size - transferred);
                if (count <= 0) break;
                transferred += count;
            }
        }
    }

    private void extractSmallFile(ZipFile zipFile, ZipEntry entry, Path targetPath) throws IOException {
        try (InputStream inputStream = zipFile.getInputStream(entry);
             OutputStream outputStream = Files.newOutputStream(targetPath,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] buffer = new byte[UNZIP_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    private void deleteDirectoriesOptimized(Path installDir) {
        List<String> directoriesToDelete = List.of(
                "mods", "config", ".fabric", "cache", ".cache"
        );

        for (String directory : directoriesToDelete) {
            try {
                deleteDirectoryRecursive(installDir.resolve(directory));
            } catch (IOException e) {
                System.err.println("Error eliminando directorio " + directory + ": " + e.getMessage());
            }
        }
    }

    private void deleteDirectoryRecursive(Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return;
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(30, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}