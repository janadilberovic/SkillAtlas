package com.skillatlas.storage;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;

@Component
public class AzureAvatarStorage implements AvatarStorage {

    private final BlobContainerClient container;
    private final Duration ttl;
    private volatile boolean containerReady;

    public AzureAvatarStorage(BlobContainerClient avatarContainer,
            @Value("${azure.storage.sas-minutes}") long sasMinutes) {
        this.container = avatarContainer;
        this.ttl = Duration.ofMinutes(sasMinutes);
    }

    /**
     * The key is built only from values the server controls. {@code getOriginalFilename()} is
     * client input and may be {@code ../../secrets.txt}; it never reaches here.
     *
     * <p>The random segment is not decoration: browsers cache by URL, so reusing
     * {@code <personId>.jpg} would show the old picture for as long as the cache holds it.
     */
    @Override
    public String store(String personId, byte[] bytes, ImageType type) {
        ensureContainer();
        String key = personId + "/" + UUID.randomUUID() + type.extension();
        BlobHttpHeaders headers = new BlobHttpHeaders()
                // The sniffed type, never the one the request claimed: an attacker who could set
                // text/html here would get a page hosted on the storage domain.
                .setContentType(type.mediaType())
                .setContentDisposition("inline");
        container.getBlobClient(key).uploadWithResponse(
                new BlobParallelUploadOptions(BinaryData.fromBytes(bytes)).setHeaders(headers),
                null, Context.NONE);
        return key;
    }

    @Override
    public void delete(String key) {
        if (key != null) {
            container.getBlobClient(key).deleteIfExists();
        }
    }

    /**
     * Signing is local HMAC-SHA256 over the URL parameters — no call to Azure — which is why
     * signing a page of people costs nothing and is not an N+1.
     */
    @Override
    public String signedUrl(String key) {
        if (key == null) {
            return null;
        }
        BlobClient blob = container.getBlobClient(key);
        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(
                OffsetDateTime.now().plus(ttl), new BlobSasPermission().setReadPermission(true))
                .setContentDisposition("inline");
        ImageType type = ImageType.fromKey(key);
        if (type != null) {
            // Overrides the response header even if some other writer ever put a blob here.
            values.setContentType(type.mediaType());
        }
        return blob.getBlobUrl() + "?" + blob.generateSas(values);
    }

    @Override
    public Duration urlTtl() {
        return ttl;
    }

    /**
     * Idempotent, like {@code SchemaInitializer} is for Neo4j constraints — a fresh machine needs
     * no manual setup step. Never the {@code PublicAccessType} overload: a public container would
     * turn every signature into decoration.
     */
    private void ensureContainer() {
        if (!containerReady) {
            container.createIfNotExists();
            containerReady = true;
        }
    }
}
