package bundle.config;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

public final class InstallerConfig {
    public final Map<String, DownloadConfig> configs;
    public final List<String> configNames;

    private InstallerConfig(Map<String, DownloadConfig> configs, List<String> configNames) {
        this.configs = Map.copyOf(configs);
        this.configNames = List.copyOf(configNames);
    }

    @Override
    public String toString() {
        return String.format("%s { configs: %s }", this.getClass().getName(), configs);
    }

    public static class Builder {
        private final Map<String, DownloadConfig> configs = new LinkedHashMap<>();
        private final List<String> configNames = new ArrayList<>();

        public Builder with(String id, DownloadConfig download) {
            configs.put(id, download);
            configNames.add(id);
            return this;
        }

        public InstallerConfig build() {
            return new InstallerConfig(configs, configNames);
        }
    }
}