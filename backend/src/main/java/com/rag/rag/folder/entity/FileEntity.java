package com.rag.rag.folder.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.rag.rag.ingestion.cache.ImageAnalysisStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "files")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String path;

    private String fileName;
    private String fileType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "image_data")
    private byte[] imageData;

    private String entityTag;

    @Enumerated(EnumType.STRING)
    private IngestionStatus ingestionStatus;

    @Column(length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    private ImageAnalysisStatus visionAnalysisStatus;

    @Enumerated(EnumType.STRING)
    private ImageAnalysisStatus faceAnalysisStatus;

    @Column(columnDefinition = "text")
    private String imageScene;

    @Column(columnDefinition = "text")
    private String imageSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String visibleTexts;
}
