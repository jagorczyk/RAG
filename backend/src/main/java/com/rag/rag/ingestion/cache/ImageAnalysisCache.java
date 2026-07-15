package com.rag.rag.ingestion.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "image_analysis_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_image_analysis_cache_key",
                columnNames = {"content_hash", "analyzer", "analyzer_version"}
        )
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageAnalysisCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImageAnalysisAnalyzer analyzer;

    @Column(name = "analyzer_version", nullable = false, length = 64)
    private String analyzerVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImageAnalysisStatus status;

    @Lob
    @Column(columnDefinition = "text")
    private String payload;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
