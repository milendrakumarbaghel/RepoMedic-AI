package org.springboot.backend;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(RepoMedicConfig.Settings.class)
public class RepoMedicConfig {
    @ConfigurationProperties("repomedic")
    public record Settings(String dataDir, boolean sandboxEnabled, String aiApiUrl,
                           String aiApiKey, String aiModel) {
        public Path root() {
            return Path.of(dataDir == null ? ".repomedic" : dataDir).toAbsolutePath().normalize();
        }

        public boolean aiEnabled() {
            return aiApiUrl != null && !aiApiUrl.isBlank() && aiApiKey != null && !aiApiKey.isBlank()
                    && aiModel != null && !aiModel.isBlank();
        }

        // Avoid the record's generated toString exposing configuration secrets.
        @Override public String toString() { return "RepoMedic settings [credentials redacted]"; }
    }

    @Bean
    MultipartConfigElement multipartConfigElement(Settings settings) throws IOException {
        Path incoming = SafeFiles.privateDirectory(settings.root().resolve("incoming"));
        return new MultipartConfigElement(incoming.toString(), 20L * 1024 * 1024,
                21L * 1024 * 1024, 0);
    }
}
