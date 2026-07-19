package com.rag.rag.ingestion.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
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

    @Test
    void appliesExifOrientationSoPortraitPixelsMatchDisplay() throws Exception {
        // 40x20 landscape raster with a red marker in the top-left.
        // EXIF Orientation 6 means "rotate 90 CW" for display → stored upright should be 20x40
        // with the marker near the top-right of the upright image.
        BufferedImage source = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 40, 20);
        g.setColor(Color.RED);
        g.fillRect(0, 0, 8, 8);
        g.dispose();

        byte[] withExif = jpegWithOrientation6(source);

        BufferedImage decoded = ImagePayloadDecoder.readOriented(withExif);

        // After Orientation 6 (90° CW), width/height swap: 40x20 → 20x40
        assertEquals(20, decoded.getWidth());
        assertEquals(40, decoded.getHeight());

        // Top-left red square becomes near top-right after rotate 90 CW.
        int rgb = decoded.getRGB(decoded.getWidth() - 4, 4);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        assertTrue(red > 200 && green < 80 && blue < 80,
                "Expected red marker near top-right after EXIF orientation, got rgb=" + rgb);
    }

    /**
     * Builds a JPEG whose pixel data is the given image and whose EXIF Orientation tag is 6
     * (RIGHT_TOP — rotate 90° clockwise for correct display).
     */
    private static byte[] jpegWithOrientation6(BufferedImage image) throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "jpg", raw));
        byte[] jpeg = raw.toByteArray();

        // Thumbnailator can apply an orientation filter when reading if EXIF is present.
        // We construct a minimal EXIF APP1 segment with Orientation=6 and splice it after SOI.
        byte[] exifApp1 = minimalExifApp1Orientation(6);
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length + exifApp1.length);
        // SOI
        out.write(jpeg, 0, 2);
        out.write(exifApp1);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] minimalExifApp1Orientation(int orientation) {
        // APP1 marker + length + "Exif\0\0" + TIFF little-endian IFD0 with Orientation tag
        // Structure based on EXIF 2.3 minimal IFD.
        byte[] payload = new byte[] {
                // APP1 marker
                (byte) 0xFF, (byte) 0xE1,
                // length (will fill): 2 bytes big-endian including length field
                0x00, 0x00,
                // Exif header
                'E', 'x', 'i', 'f', 0, 0,
                // TIFF header LE
                'I', 'I',
                0x2A, 0x00,             // magic 42
                0x08, 0x00, 0x00, 0x00, // offset to first IFD = 8
                // IFD0: 1 entry
                0x01, 0x00,
                // Entry: Orientation (0x0112), SHORT (3), count 1, value
                0x12, 0x01,
                0x03, 0x00,
                0x01, 0x00, 0x00, 0x00,
                (byte) orientation, 0x00, 0x00, 0x00,
                // next IFD offset
                0x00, 0x00, 0x00, 0x00
        };
        int length = payload.length - 2; // excludes marker, includes length bytes
        payload[2] = (byte) ((length >> 8) & 0xFF);
        payload[3] = (byte) (length & 0xFF);
        return payload;
    }

    private byte[] imageBytes(String format, int imageType) throws Exception {
        BufferedImage image = new BufferedImage(4, 3, imageType);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output));
        return output.toByteArray();
    }
}
