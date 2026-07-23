package com.rag.rag.knowledge.query;

import com.rag.rag.knowledge.graph.EntityVisualAnchor;
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
import java.util.List;

/** Draws only graph-confirmed identity boxes on the temporary image sent to the verifier. */
@UtilityClass
class EvidenceImageRenderer {
    byte[] render(byte[] original, List<EntityVisualAnchor> anchors) {
        if (original == null || original.length == 0 || anchors == null || anchors.isEmpty()) return original;
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) return original;
            BufferedImage output = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = output.createGraphics();
            graphics.drawImage(source, 0, 0, null);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setStroke(new BasicStroke(Math.max(3f, source.getWidth() / 400f)));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(18, source.getWidth() / 45)));
            for (EntityVisualAnchor anchor : anchors) {
                if (anchor.bbox().size() < 4 || anchor.entityName() == null || anchor.entityName().isBlank()) continue;
                int x1 = Math.round(anchor.bbox().get(0));
                int y1 = Math.round(anchor.bbox().get(1));
                int x2 = Math.round(anchor.bbox().get(2));
                int y2 = Math.round(anchor.bbox().get(3));
                graphics.setColor(Color.MAGENTA);
                graphics.drawRect(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
                int baseline = Math.max(graphics.getFontMetrics().getHeight(), y1 - 4);
                graphics.fillRect(x1, baseline - graphics.getFontMetrics().getAscent(),
                        graphics.getFontMetrics().stringWidth(anchor.entityName()) + 8,
                        graphics.getFontMetrics().getHeight());
                graphics.setColor(Color.WHITE);
                graphics.drawString(anchor.entityName(), x1 + 4, baseline);
            }
            graphics.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(output, "jpg", bytes);
            return bytes.toByteArray();
        } catch (Exception ignored) {
            return original;
        }
    }
}
