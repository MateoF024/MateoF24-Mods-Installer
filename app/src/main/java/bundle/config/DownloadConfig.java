    package bundle.config;

    import java.util.List;

    public final class DownloadConfig {
        public final String name;
        public final List<String> urls;

        public DownloadConfig(String name, String url) {
            this.name = name;
            this.urls = List.of(url); // Convertir URL única a lista para compatibilidad con DownloadManager
        }

        // Constructor alternativo que acepta múltiples URLs (por si en el futuro se necesita)
        public DownloadConfig(String name, List<String> urls) {
            this.name = name;
            this.urls = urls == null ? List.of() : List.copyOf(urls);
        }

        @Override
        public String toString() {
            return "DownloadConfig{" +
                    "name='" + name + '\'' +
                    ", urls=" + urls +
                    '}';
        }
    }