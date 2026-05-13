package com.rag.rag.ingestion.detector;

import org.opencv.core.*;
import org.opencv.dnn.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class TextDetector {

    private final TextDetectionModel_DB model;

    public Rect lastDetectedRect = null;

    public TextDetector(String onnxPath) {
        Net net = Dnn.readNetFromONNX(onnxPath);
        this.model = new TextDetectionModel_DB(net);
        this.model.setBinaryThreshold(0.3f);
        this.model.setPolygonThreshold(0.5f);
        this.model.setUnclipRatio(2.0);
        this.model.setMaxCandidates(200);
        this.model.setInputParams(1.0 / 255.0, new Size(736, 736), new Scalar(122.67, 116.66, 104.00));
    }

    public Mat detectAndCrop(Mat src) {
        if (src.empty()) return null;
        this.lastDetectedRect = null;

        List<MatOfPoint> results = new ArrayList<>();
        MatOfFloat confidences = new MatOfFloat();
        model.detect(src, results, confidences);

        if (results.isEmpty()) return null;

        Rect textBoundingBox = null;
        for (MatOfPoint contour : results) {
            Rect r = Imgproc.boundingRect(contour);
            if (textBoundingBox == null) textBoundingBox = r;
            else textBoundingBox = createBoundingRect(textBoundingBox, r);
        }

        Rect labelContainer = findEnclosingLabel(src, textBoundingBox);

        if (labelContainer != null) {
            this.lastDetectedRect = labelContainer;
            return safeCrop(src, labelContainer, 10);
        } else {
            this.lastDetectedRect = textBoundingBox;
            return safeCrop(src, textBoundingBox, 50);
        }
    }

    private Rect findEnclosingLabel(Mat src, Rect textRect) {
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);

        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 50, 150);
        Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Rect bestContainer = null;
        double minAreaDiff = Double.MAX_VALUE;
        double textArea = textRect.width * textRect.height;
        Point textCenter = new Point(textRect.x + textRect.width / 2.0, textRect.y + textRect.height / 2.0);

        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            double rectArea = rect.width * rect.height;

            if (rectArea > textArea && rectArea < (src.width() * src.height() * 0.95)) {
                if (rect.contains(textCenter)) {
                    double diff = rectArea - textArea;
                    if (diff < minAreaDiff) {
                        minAreaDiff = diff;
                        bestContainer = rect;
                    }
                }
            }
        }

        gray.release();
        edges.release();
        hierarchy.release();
        return bestContainer;
    }

    private Rect createBoundingRect(Rect r1, Rect r2) {
        int x = Math.min(r1.x, r2.x);
        int y = Math.min(r1.y, r2.y);
        int right = Math.max(r1.x + r1.width, r2.x + r2.width);
        int bottom = Math.max(r1.y + r1.height, r2.y + r2.height);
        return new Rect(x, y, right - x, bottom - y);
    }

    private Mat safeCrop(Mat src, Rect box, int padding) {
        int x = Math.max(box.x - padding, 0);
        int y = Math.max(box.y - padding, 0);
        int w = Math.min(box.width + (2 * padding), src.width() - x);
        int h = Math.min(box.height + (2 * padding), src.height() - y);
        if (w <= 0 || h <= 0) return src;
        return new Mat(src, new Rect(x, y, w, h));
    }
}