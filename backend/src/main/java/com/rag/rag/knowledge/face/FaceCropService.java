package com.rag.rag.knowledge.face;

import com.rag.rag.folder.entity.FileEntity;
import com.rag.rag.folder.repository.FileRepository;
import com.rag.rag.knowledge.repository.FaceEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaceCropService {

    private static final int CROP_SIZE = 120;

    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FileRepository fileRepository;

    public Optional<String> cropFaceBase64ForMention(UUID mentionId) {
        return faceEmbeddingRepository.findFirstByMention_Id(mentionId)
                .flatMap(embedding -> cropFaceBase64(embedding.getFilePath(), embedding.getBbox()));
    }

    public Optional<String> cropFaceBase64(String filePath, float[] bbox) {
        return cropFaceBytes(filePath, bbox).map(bytes -> java.util.Base64.getEncoder().encodeToString(bytes));
    }

    public Optional<byte[]> cropFaceBytes(String filePath, float[] bbox) {
        if (bbox == null || bbox.length < 4) {
            return Optional.empty();
        }

        Optional<FileEntity> fileOpt = fileRepository.findByPath(filePath);
        if (fileOpt.isEmpty()) {
            return Optional.empty();
        }

        byte[] imageData = fileOpt.get().getImageData();
        if (imageData == null || imageData.length == 0) {
            return Optional.empty();
        }

        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageData));
            if (source == null) {
                return Optional.empty();
            }

            int[] region = paddedRegion(bbox, source.getWidth(), source.getHeight());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thumbnails.of(source)
                    .sourceRegion(region[0], region[1], region[2], region[3])
                    .size(CROP_SIZE, CROP_SIZE)
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .toOutputStream(output);
            return Optional.of(output.toByteArray());
        } catch (Exception e) {
            log.warn("Failed to crop face from {}: {}", filePath, e.getMessage());
            return Optional.empty();
        }
    }

    private int[] paddedRegion(float[] bbox, int imageWidth, int imageHeight) {
        float x1 = bbox[0];
        float y1 = bbox[1];
        float x2 = bbox[2];
        float y2 = bbox[3];
        float width = Math.max(1f, x2 - x1);
        float height = Math.max(1f, y2 - y1);
        float padX = width * 0.18f;
        float padY = height * 0.18f;

        int left = Math.max(0, (int) Math.floor(x1 - padX));
        int top = Math.max(0, (int) Math.floor(y1 - padY));
        int right = Math.min(imageWidth, (int) Math.ceil(x2 + padX));
        int bottom = Math.min(imageHeight, (int) Math.ceil(y2 + padY));

        int cropWidth = Math.max(1, right - left);
        int cropHeight = Math.max(1, bottom - top);

        if (left + cropWidth > imageWidth) {
            cropWidth = imageWidth - left;
        }
        if (top + cropHeight > imageHeight) {
            cropHeight = imageHeight - top;
        }

        return new int[]{left, top, cropWidth, cropHeight};
    }
}
