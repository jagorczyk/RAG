package com.rag.rag.Detectors;

import org.opencv.core.*;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class ROIDetector {

    private final TextDetector textDetector;
    private final YoloDetector yoloDetector;

    private static final double CROP_THRESHOLD = 0.7;

    private static final List<String> ANIMALS = Arrays.asList("cat", "dog", "horse", "bird");

    public ROIDetector(String textModelPath, String yoloModelPath) {
        this.textDetector = new TextDetector(textModelPath);
        this.yoloDetector = new YoloDetector(yoloModelPath);
    }

    public Mat detectAndChooseBest(Mat src) {
        if (src.empty()) return null;

        Mat cropYolo = yoloDetector.detectAndCrop(src.clone());
        Rect rectYolo = yoloDetector.lastDetectedRect;
        String yoloClass = yoloDetector.lastDetectedClass;
        float yoloConf = yoloDetector.lastConfidence;

        Mat cropText = textDetector.detectAndCrop(src.clone());
        Rect rectText = textDetector.lastDetectedRect;

        double scoreYoloTotal = 0.0;
        if (cropYolo != null) {
            double focus = calculateFocusScore(src, rectYolo);
            scoreYoloTotal = (yoloConf * 0.6) + (focus * 0.4);

            if ("person".equals(yoloClass) && (rectYolo.y + rectYolo.height > src.height() * 0.95)) {
                scoreYoloTotal *= 0.5;
            }
            if (ANIMALS.contains(yoloClass)) {
                scoreYoloTotal *= 1.2;
            }
        }

        double scoreTextTotal = 0.0;
        if (cropText != null) {
            scoreTextTotal = calculateFocusScore(src, rectText);
            scoreTextTotal *= 1.1;
        }

        Mat winnerCrop = null;
        double winnerScore = 0.0;

        if (scoreTextTotal > scoreYoloTotal) {
            winnerCrop = cropText;
            winnerScore = scoreTextTotal;
            if (cropYolo != null) cropYolo.release();
        } else {
            winnerCrop = cropYolo;
            winnerScore = scoreYoloTotal;
            if (cropText != null) cropText.release();
        }

        if (winnerCrop == null || winnerScore < CROP_THRESHOLD) {
            if (winnerCrop != null) winnerCrop.release();
            return src.clone();
        }

        return winnerCrop;
    }

    private double calculateFocusScore(Mat img, Rect rect) {
        if (rect == null) return 0;
        double imgArea = img.width() * img.height();
        double imgCenterX = img.width() / 2.0;
        double imgCenterY = img.height() / 2.0;
        double areaRatio = (rect.width * rect.height) / imgArea;
        double objCenterX = rect.x + (rect.width / 2.0);
        double objCenterY = rect.y + (rect.height / 2.0);
        double distX = Math.abs(imgCenterX - objCenterX);
        double distY = Math.abs(imgCenterY - objCenterY);
        double maxDistX = img.width() / 2.0;
        double maxDistY = img.height() / 2.0;
        double centrality = ((1.0 - (distX / maxDistX)) + (1.0 - (distY / maxDistY))) / 2.0;

        return (centrality * 0.7) + (areaRatio * 0.3);
    }
}