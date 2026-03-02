package com.rag.rag.Detectors;

import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class YoloDetector {

    private static final List<String> COCO_CLASSES = Arrays.asList(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
            "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"
    );

    private static final List<Integer> PRIORITY_CLASSES = Arrays.asList(0, 15, 16, 17);

    private final Net net;

    public String lastDetectedClass = "";
    public Rect lastDetectedRect = null;
    public float lastConfidence = 0f;

    public YoloDetector(String onnxPath) {
        this.net = Dnn.readNetFromONNX(onnxPath);
    }

    public Mat detectAndCrop(Mat src) {
        if (src.empty()) return null;

        this.lastDetectedClass = "";
        this.lastDetectedRect = null;
        this.lastConfidence = 0f;

        Mat blob = Dnn.blobFromImage(src, 1 / 255.0, new Size(640, 640), new Scalar(0, 0, 0), true, false);
        net.setInput(blob);
        Mat output = net.forward();

        Mat output2D = output.reshape(1, 84);
        Mat tOutput = output2D.t();
        float[] data = new float[(int) tOutput.total()];
        tOutput.get(0, 0, data);

        int rows = tOutput.rows();
        int cols = tOutput.cols();
        float xFactor = (float) src.width() / 640.0f;
        float yFactor = (float) src.height() / 640.0f;

        Rect2d bestBox = null;
        float bestConfidence = 0f;
        int bestClassId = -1;

        for (int i = 0; i < rows; i++) {
            int index = i * cols;
            float maxScore = 0f;
            int maxClassId = -1;

            for (int j = 4; j < cols; j++) {
                if (data[index + j] > maxScore) {
                    maxScore = data[index + j];
                    maxClassId = j - 4;
                }
            }

            if (maxScore > 0.5f) {
                if (PRIORITY_CLASSES.contains(maxClassId)) {
                    if (maxScore > bestConfidence) {
                        bestConfidence = maxScore;
                        bestClassId = maxClassId;
                        float cx = data[index];
                        float cy = data[index + 1];
                        float w = data[index + 2];
                        float h = data[index + 3];
                        int left = (int) ((cx - w / 2) * xFactor);
                        int top = (int) ((cy - h / 2) * yFactor);
                        int width = (int) (w * xFactor);
                        int height = (int) (h * yFactor);
                        bestBox = new Rect2d(left, top, width, height);
                    }
                }
            }
        }

        blob.release();
        output.release();
        output2D.release();
        tOutput.release();

        if (bestBox != null) {
            this.lastDetectedClass = COCO_CLASSES.get(bestClassId);
            this.lastDetectedRect = new Rect((int) bestBox.x, (int) bestBox.y, (int) bestBox.width, (int) bestBox.height);
            this.lastConfidence = bestConfidence;

            return safeCrop(src, bestBox, 20);
        }

        return findLabelByTexture(src);
    }

    private Mat findLabelByTexture(Mat src) {

        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        Mat morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Mat grad = new Mat();
        Imgproc.morphologyEx(gray, grad, Imgproc.MORPH_GRADIENT, morphKernel);
        Mat bw = new Mat();
        Imgproc.threshold(grad, bw, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        Mat connectKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(17, 3));
        Mat connected = new Mat();
        Imgproc.morphologyEx(bw, connected, Imgproc.MORPH_CLOSE, connectKernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(connected, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Rect bestRect = null;
        double maxArea = 0;

        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            double area = rect.width * rect.height;
            double aspectRatio = (double) rect.width / rect.height;

            if (area > 2000 && area < (src.width() * src.height() * 0.9) && aspectRatio > 0.8) {
                if (area > maxArea) {
                    maxArea = area;
                    bestRect = rect;
                }
            }
        }

        gray.release(); grad.release(); bw.release(); connected.release();
        morphKernel.release(); connectKernel.release(); hierarchy.release();

        if (bestRect != null) {
            this.lastDetectedClass = "texture_label";
            this.lastDetectedRect = bestRect;
            this.lastConfidence = 0.65f;
            return safeCrop(src, new Rect2d(bestRect.x, bestRect.y, bestRect.width, bestRect.height), 15);
        }
        return null;
    }

    private Mat safeCrop(Mat src, Rect2d box, int padding) {
        int x = (int) Math.max(box.x - padding, 0);
        int y = (int) Math.max(box.y - padding, 0);
        int w = (int) Math.min(box.width + (2 * padding), src.width() - x);
        int h = (int) Math.min(box.height + (2 * padding), src.height() - y);
        if (w <= 0 || h <= 0) return null;
        return new Mat(src, new Rect(x, y, w, h));
    }
}