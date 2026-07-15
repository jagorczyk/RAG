package com.rag.rag.ingestion.cache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImageAnalysisCacheRepository extends JpaRepository<ImageAnalysisCache, Long> {

    Optional<ImageAnalysisCache> findByContentHashAndAnalyzerAndAnalyzerVersion(
            String contentHash,
            ImageAnalysisAnalyzer analyzer,
            String analyzerVersion
    );
}
