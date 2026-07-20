package com.rag.rag.folder.dto;

public record UploadResultDto(
        String path,
        String fileName,
        boolean image,
        String status
) {
    public UploadResultDto(String path, String fileName, boolean image) {
        this(path, fileName, image, null);
    }
}
