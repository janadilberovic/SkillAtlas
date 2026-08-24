package com.skillatlas.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

@Configuration
public class BlobStorageConfig {

    /**
     * Building the client touches no network — the container is created on first upload instead
     * (see {@link AzureAvatarStorage}). Doing it here would make every {@code @SpringBootTest}
     * context, avatar-related or not, refuse to start unless Azurite happened to be running.
     */
    @Bean
    public BlobContainerClient avatarContainer(
            @Value("${azure.storage.connection-string}") String connectionString,
            @Value("${azure.storage.container}") String container) {
        return new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient()
                .getBlobContainerClient(container);
    }
}
