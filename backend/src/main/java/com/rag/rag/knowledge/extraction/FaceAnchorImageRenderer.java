package com.rag.rag.knowledge.extraction;

import com.rag.rag.knowledge.face.DetectedFaceDto;
import lombok.experimental.UtilityClass;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders a temporary analysis copy with stable face ids; original file bytes are never changed. */
@UtilityClass
public class FaceAnchorImageRenderer {

    public AnchoredImage render(byte[] imageBytes, String format, List<DetectedFaceDto> detectedFaces) {
        if (imageBytes == null || imageBytes.length == 0 || detectedFaces == null || detectedFaces.isEmpty()) {
            return new AnchoredImage(imageBytes, Map.of());
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (source == null) return new AnchoredImage(imageBytes, Map.of());
            BufferedImage annotated = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = annotated.createGraphics();
            graphics.drawImage(source, 0, 0, null);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setStroke(new BasicStroke(Math.max(3f, source.getWidth() / 400f)));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(18, source.getWidth() / 45)));

            List<DetectedFaceDto> ordered = detectedFaces.stream()
                    .filter(face -> face != null && face.bbox() != null && face.bbox().size() >= 4)
                    .sorted(Comparator.comparingDouble(face -> (face.bbox().get(0) + face.bbox().get(2)) / 2.0))
                    .toList();
            Map<String, DetectedFaceDto> anchors = new LinkedHashMap<>();
            for (int index = 0; index < ordered.size(); index++) {
                DetectedFaceDto face = ordered.get(index);
                String anchor = "face_" + (index + 1);
                anchors.put(anchor, face);
                int x1 = Math.round(face.bbox().get(0));
                int y1 = Math.round(face.bbox().get(1));
                int x2 = Math.round(face.bbox().get(2));
                int y2 = Math.round(face.bbox().get(3));
                graphics.setColor(Color.MAGENTA);
                graphics.drawRect(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
                int textY = Math.max(graphics.getFontMetrics().getHeight(), y1 - 4);
                graphics.fillRect(x1, textY - graphics.getFontMetrics().getAscent(),
                        graphics.getFontMetrics().stringWidth(anchor) + 8, graphics.getFontMetrics().getHeight());
                graphics.setColor(Color.WHITE);
                graphics.drawString(anchor, x1 + 4, textY);
            }
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            String outputFormat = "png".equalsIgnoreCase(format) ? "png" : "jpg";
            ImageIO.write(annotated, outputFormat, output);
            return new AnchoredImage(output.toByteArray(), Map.copyOf(anchors));
        } catch (Exception ignored) {
            return new AnchoredImage(imageBytes, Map.of());
        }
    }

    public record AnchoredImage(byte[] bytes, Map<String, DetectedFaceDto> anchors) {
        public AnchoredImage {
            anchors = anchors == null ? Map.of() : Map.copyOf(anchors);
        }
    }
}
