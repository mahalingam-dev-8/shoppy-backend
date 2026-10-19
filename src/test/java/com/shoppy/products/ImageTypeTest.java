package com.shoppy.products;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageTypeTest {

    @Test
    void detectsPngFromItsSignature() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(ImageType.detect(png)).contains(ImageType.PNG);
    }

    @Test
    void detectsJpegFromItsSignature() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        assertThat(ImageType.detect(jpeg)).contains(ImageType.JPEG);
    }

    @Test
    void detectsWebpFromItsSignature() {
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(ImageType.detect(webp)).contains(ImageType.WEBP);
    }

    @Test
    void rejectsATextFileRenamedToAnImage() {
        // The client may call it photo.png with Content-Type image/png; the bytes decide.
        byte[] text = "this is not an image".getBytes(StandardCharsets.UTF_8);
        assertThat(ImageType.detect(text)).isEmpty();
    }

    @Test
    void rejectsInputTooShortToIdentify() {
        assertThat(ImageType.detect(new byte[] {(byte) 0xFF})).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
    }
}
