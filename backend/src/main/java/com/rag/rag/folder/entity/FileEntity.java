package com.rag.rag.folder.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.rag.rag.ingestion.cache.ImageAnalysisStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

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

    @Column(name = "owner_id")
    private UUID ownerId;

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

    /** JSON: open scene attributes {background, setting, lighting} — no domain enums. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scene_attributes", columnDefinition = "jsonb")
    private String sceneAttributes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String visibleTexts;

    /** Complete structured vision payload retained for graph-side validation. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String structuredVisionContext;

    @Column(name = "graph_projection_version", length = 64)
    private String graphProjectionVersion;

    @Column(name = "graph_projection_status", length = 32)
    private String graphProjectionStatus;
}
