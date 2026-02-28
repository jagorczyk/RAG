package com.rag.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RagApplication {

	public static void main(String[] args) {
		nu.pattern.OpenCV.loadLocally();
		SpringApplication.run(RagApplication.class, args);
	}

}
