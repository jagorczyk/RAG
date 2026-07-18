package com.rag.rag.ingestion.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

final class ImagePayloadDecoder {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("jpg", "png");

    private ImagePayloadDecoder() {
    }

    static DecodedImage decode(byte[] imageData, String fileName) {
        if (imageData == null || imageData.length == 0) {
            throw invalid(fileName, "Plik jest pusty.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(imageData))) {
            if (input == null) {
                throw invalid(fileName, "Nie udało się odczytać danych pliku.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid(fileName,
                        "Zawartość nie jest obsługiwanym obrazem JPEG lub PNG. Plik może być uszkodzony albo mieć błędne rozszerzenie.");
            }

            ImageReader reader = readers.next();
            try {
                String format = normalizeFormat(reader.getFormatName());
                if (!SUPPORTED_FORMATS.contains(format)) {
                    throw invalid(fileName, "Format obrazu " + reader.getFormatName()
                            + " nie jest obsługiwany. Użyj pliku JPEG lub PNG.");
                }

                reader.setInput(input, true, true);
                BufferedImage image = reader.read(0);
                if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                    throw invalid(fileName, "Nie udało się zdekodować obrazu.");
                }
                return new DecodedImage(image, format, "png".equals(format) ? "image/png" : "image/jpeg");
            } finally {
                reader.dispose();
            }
        } catch (InvalidImageException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new InvalidImageException(messagePrefix(fileName)
                    + "Nie udało się zdekodować obrazu. Plik może być uszkodzony.", e);
        }
    }

    private static String normalizeFormat(String formatName) {
        String normalized = formatName.toLowerCase(Locale.ROOT);
        return "jpeg".equals(normalized) ? "jpg" : normalized;
    }

    private static InvalidImageException invalid(String fileName, String detail) {
        return new InvalidImageException(messagePrefix(fileName) + detail);
    }

    private static String messagePrefix(String fileName) {
        String safeName = fileName == null || fileName.isBlank() ? "Przesłany plik" : "Plik „" + fileName + "”";
        return safeName + ": ";
    }

    record DecodedImage(BufferedImage image, String format, String mimeType) {
    }
}
