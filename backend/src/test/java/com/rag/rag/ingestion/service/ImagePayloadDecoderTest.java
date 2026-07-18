package com.rag.rag.ingestion.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagePayloadDecoderTest {

    @Test
    void rejectsEmptyFileWithReadableMessage() {
        InvalidImageException error = assertThrows(InvalidImageException.class,
                () -> ImagePayloadDecoder.decode(new byte[0], "empty.jpg"));

        assertTrue(error.getMessage().contains("Plik jest pusty"));
    }

    @Test
    void rejectsDataThatIsNotAnImage() {
        InvalidImageException error = assertThrows(InvalidImageException.class,
                () -> ImagePayloadDecoder.decode("not an image".getBytes(), "fake.jpg"));

        assertTrue(error.getMessage().contains("nie jest obsługiwanym obrazem"));
    }

    @Test
    void detectsPngFromContentEvenWhenFileHasJpegExtension() throws Exception {
        byte[] png = imageBytes("png", BufferedImage.TYPE_INT_ARGB);

        ImagePayloadDecoder.DecodedImage decoded = ImagePayloadDecoder.decode(png, "photo.jpg");

        assertEquals("png", decoded.format());
        assertEquals("image/png", decoded.mimeType());
        assertEquals(4, decoded.image().getWidth());
    }

    @Test
    void decodesValidJpeg() throws Exception {
        byte[] jpeg = imageBytes("jpg", BufferedImage.TYPE_INT_RGB);

        ImagePayloadDecoder.DecodedImage decoded = ImagePayloadDecoder.decode(jpeg, "photo.jpg");

        assertEquals("jpg", decoded.format());
        assertEquals("image/jpeg", decoded.mimeType());
    }

    private byte[] imageBytes(String format, int imageType) throws Exception {
        BufferedImage image = new BufferedImage(4, 3, imageType);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }
}
