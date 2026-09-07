package org.example.festivalservice.domain.festival;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 페스티벌 등록 전 이미지 파일을 먼저 저장하고 URL만 돌려주는 서비스.
 * Festival 엔티티·DB를 전혀 알지 못한다 — 등록 시점에 이 URL을 FestivalRequestDto.imageUrls로
 * 그대로 실어 보내면 FestivalService가 그때 FestivalImage로 엮어 저장한다.
 */
@Service
@RequiredArgsConstructor
public class FestivalImageUploadService {

    private static final String HOST_ROLE = "HOST";
    private static final int MAX_IMAGE_COUNT = 3;
    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final String IMAGE_URL_PREFIX = "/api/festivals/images/";

    @Value("${festival.image.saved-path:./savedimage}")
    private String savedImagePath;

    public List<String> upload(String role, List<MultipartFile> files) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_HOST_ROLE);
        }
        if (files == null || files.isEmpty() || files.size() > MAX_IMAGE_COUNT) {
            throw new ApiException(FestivalErrorCode.INVALID_IMAGE_COUNT);
        }

        return files.stream().map(this::storeSingle).toList();
    }

    private String storeSingle(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new ApiException(FestivalErrorCode.INVALID_IMAGE_SIZE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApiException(FestivalErrorCode.INVALID_IMAGE_TYPE);
        }

        String filename = UUID.randomUUID() + extractExtension(file.getOriginalFilename());
        try {
            Path directory = Path.of(savedImagePath);
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(filename));
        } catch (IOException e) {
            throw new ApiException(FestivalErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return IMAGE_URL_PREFIX + filename;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        return dotIndex == -1 ? "" : originalFilename.substring(dotIndex);
    }
}
