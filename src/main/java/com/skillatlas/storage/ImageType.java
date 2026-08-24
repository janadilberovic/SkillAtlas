package com.skillatlas.storage;

/**
 * The image formats an avatar may be, recognised by their magic bytes.
 *
 * <p>The multipart {@code Content-Type} is written by the client, so {@code file.getContentType()}
 * is a claim, not evidence: {@code curl -F "file=@payload.svg;type=image/png"} passes any check
 * based on it. The first bytes of the body cannot be talked into lying the same way.
 *
 * <p>SVG is deliberately absent — it is XML and may carry {@code <script>}, which served from a
 * storage domain is stored XSS (spec §5).
 */
public enum ImageType {

    JPEG("image/jpeg", ".jpg"),
    PNG("image/png", ".png"),
    WEBP("image/webp", ".webp");

    private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG_MAGIC =
            { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] RIFF_MAGIC = { 'R', 'I', 'F', 'F' };
    private static final byte[] WEBP_MAGIC = { 'W', 'E', 'B', 'P' };

    private final String mediaType;
    private final String extension;

    ImageType(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }

    /** @return the recognised type, or {@code null} when the bytes are not a supported image */
    public static ImageType sniff(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (startsWith(bytes, 0, JPEG_MAGIC)) {
            return JPEG;
        }
        if (startsWith(bytes, 0, PNG_MAGIC)) {
            return PNG;
        }
        // WEBP is a RIFF container: "RIFF", four length bytes, then "WEBP".
        if (startsWith(bytes, 0, RIFF_MAGIC) && startsWith(bytes, 8, WEBP_MAGIC)) {
            return WEBP;
        }
        return null;
    }

    /** @return the type implied by a blob key's extension, or {@code null} */
    public static ImageType fromKey(String key) {
        if (key == null) {
            return null;
        }
        for (ImageType type : values()) {
            if (key.endsWith(type.extension)) {
                return type;
            }
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] magic) {
        if (bytes.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
