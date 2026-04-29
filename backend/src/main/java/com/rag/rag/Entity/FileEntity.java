package com.rag.rag.Entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "files")
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

    public FileEntity() {}

    public FileEntity(Long id, String path, String fileName, String fileType, byte[] imageData) {
        this.id = id;
        this.path = path;
        this.fileName = fileName;
        this.fileType = fileType;
        this.imageData = imageData;
    }

    public static FileEntityBuilder builder() {
        return new FileEntityBuilder();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }

    public static class FileEntityBuilder {
        private Long id;
        private String path;
        private String fileName;
        private String fileType;
        private byte[] imageData;

        public FileEntityBuilder id(Long id) { this.id = id; return this; }
        public FileEntityBuilder path(String path) { this.path = path; return this; }
        public FileEntityBuilder fileName(String fileName) { this.fileName = fileName; return this; }
        public FileEntityBuilder fileType(String fileType) { this.fileType = fileType; return this; }
        public FileEntityBuilder imageData(byte[] imageData) { this.imageData = imageData; return this; }

        public FileEntity build() {
            return new FileEntity(id, path, fileName, fileType, imageData);
        }
    }
}
