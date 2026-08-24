package com.skillatlas.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** The upload gate: what the bytes are, not what the request claimed they are. */
class ImageTypeTest {

    private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01);
    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10);

    @Test
    void recognisesPng() {
        assertThat(ImageType.sniff(PNG)).isEqualTo(ImageType.PNG);
    }

    @Test
    void recognisesJpeg() {
        assertThat(ImageType.sniff(JPEG)).isEqualTo(ImageType.JPEG);
    }

    @Test
    void recognisesWebp() {
        byte[] webp = bytes('R', 'I', 'F', 'F', 0x1A, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P');
        assertThat(ImageType.sniff(webp)).isEqualTo(ImageType.WEBP);
    }

    // A RIFF container that is not WEBP (a WAV, say) must not slip through on the first four bytes.
    @Test
    void rejectsRiffThatIsNotWebp() {
        byte[] wav = bytes('R', 'I', 'F', 'F', 0x1A, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E');
        assertThat(ImageType.sniff(wav)).isNull();
    }

    // The whole point of sniffing: an SVG announced as image/png is still an SVG.
    @Test
    void rejectsSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(ImageType.sniff(svg)).isNull();
    }

    @Test
    void rejectsEmptyAndTruncatedInput() {
        assertThat(ImageType.sniff(null)).isNull();
        assertThat(ImageType.sniff(new byte[0])).isNull();
        assertThat(ImageType.sniff(bytes(0x89, 'P'))).isNull();
    }

    @Test
    void mapsKeyExtensionBackToTheMediaType() {
        assertThat(ImageType.fromKey("p1/9f3e.png")).isEqualTo(ImageType.PNG);
        assertThat(ImageType.fromKey("p1/9f3e.jpg")).isEqualTo(ImageType.JPEG);
        assertThat(ImageType.fromKey("p1/9f3e.bin")).isNull();
        assertThat(ImageType.fromKey(null)).isNull();
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
