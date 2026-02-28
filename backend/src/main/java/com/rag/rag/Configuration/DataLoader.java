package com.rag.rag.Configuration;

import com.rag.rag.Detectors.ROIDetector;
import com.rag.rag.Detectors.TextDetector;
import com.rag.rag.Detectors.YoloDetector;
import com.rag.rag.Service.IngestionService;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class DataLoader {

    @Bean
    CommandLineRunner loadData() {
        return args -> {
//            nu.pattern.OpenCV.loadLocally();
//
//            String imagePath = "C:/Users/mrigo/springboot/RAG/backend/data/test/leo2.jpg";
//            String yoloPath = "C:/Users/mrigo/springboot/RAG/backend/data/models/yolo11x.onnx";
//            String textModelPath = "C:/Users/mrigo/springboot/RAG/backend/data/models/DB_TD500_resnet50.onnx";
//
//            if (!new File(imagePath).exists()) {
//                System.err.println("BŁĄD: Brak pliku obrazu!");
//                return;
//            }
//
//            Mat src = Imgcodecs.imread(imagePath);
//            if (src.empty()) return;
//            System.out.println("Oryginał: " + src.width() + "x" + src.height());
//
//            // =================================================================
//            // METODA 1: YoloDetector (Morfologia / Gradient)
//            // =================================================================
//            try {
//                System.out.println("\n--- 1. YoloDetector (Morfologia) ---");
//                YoloDetector yoloDetector = new YoloDetector(yoloPath);
//                Mat resultYolo = yoloDetector.detectAndCrop(src.clone());
//
//                if (resultYolo != null) {
//                    Imgcodecs.imwrite("wynik_1_yolo_morph.jpg", resultYolo);
//                    System.out.println(">>> ZAPISANO: wynik_1_yolo_morph.jpg");
//                } else {
//                    System.out.println(">>> Brak wyniku.");
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//
//            // =================================================================
//            // METODA 2: TextDetector (AI DB Model)
//            // =================================================================
//            try {
//                System.out.println("\n--- 2. TextDetector (AI) ---");
//                if (new File(textModelPath).exists()) {
//                    TextDetector textDetector = new TextDetector(textModelPath);
//                    Mat resultText = textDetector.detectAndCrop(src.clone());
//
//                    if (resultText != null) {
//                        Imgcodecs.imwrite("wynik_2_text_ai.jpg", resultText);
//                        System.out.println(">>> ZAPISANO: wynik_2_text_ai.jpg");
//                    } else {
//                        System.out.println(">>> Brak wyniku.");
//                    }
//                } else {
//                    System.err.println("Pominięto Metodę 2 (brak modelu DB .onnx)");
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//
//            // =================================================================
//            // METODA 3: HybridDetector (Sędzia - wybiera najlepszy z powyższych)
//            // =================================================================
//            try {
//                System.out.println("\n--- 3. HybridDetector (SĘDZIA) ---");
//                if (new File(textModelPath).exists() && new File(yoloPath).exists()) {
//
//                    ROIDetector hybrid = new ROIDetector(textModelPath, yoloPath);
//                    // Tutaj hybryda sama uruchamia obie metody wewnątrz i porównuje wyniki
//                    Mat resultHybrid = hybrid.detectAndChooseBest(src.clone());
//
//                    if (resultHybrid != null) {
//                        Imgcodecs.imwrite("wynik_3_hybrid.jpg", resultHybrid);
//                        System.out.println(">>> ZAPISANO NAJLEPSZY WYNIK: wynik_3_hybrid.jpg");
//                    } else {
//                        System.out.println(">>> Hybryda nic nie wybrała.");
//                    }
//                } else {
//                    System.err.println("Nie można uruchomić hybrydy (brak jednego z modeli).");
//                }
//            } catch (Exception e) {
//                System.err.println("Błąd w HybridDetector: " + e.getMessage());
//                e.printStackTrace();
//            }
        };
    }
}