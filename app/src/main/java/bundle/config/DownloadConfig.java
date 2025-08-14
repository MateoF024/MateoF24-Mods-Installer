package bundle.config;

import java.util.List;

public final class DownloadConfig {
    public final String name;
    public final List<String> urls;

    public DownloadConfig(String name, String url) {
        this.name = name;
        this.urls = List.of(url);
    }

    @Override
    public String toString() {
        return "DownloadConfig{" +
                "name='" + name + '\'' +
                ", urls=" + urls +
                '}';
    }
}