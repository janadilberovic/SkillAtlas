package com.skillatlas.storage;

import java.time.Duration;

/**
 * Where profile pictures live. The only reason this is an interface is that
 * {@link AzureAvatarStorage} is then the single file that knows Azure exists — swapping to S3
 * changes one class and one bean, and leaves callers and tests untouched.
 */
public interface AvatarStorage {

    /** @return the blob key to store on the person; never the URL, which expires */
    String store(String personId, byte[] bytes, ImageType type);

    /** Best effort: a key whose blob is already gone is not an error. */
    void delete(String key);

    /** @return a short-lived read-only URL, or {@code null} for a {@code null} key */
    String signedUrl(String key);

    /** How long {@link #signedUrl} stays valid, so callers can tell the client when to refetch. */
    Duration urlTtl();
}
