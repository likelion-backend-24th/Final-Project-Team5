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
    private static final int MAX_THUMBNAIL_COUNT = 1;
    private static final int MAX_DETAIL_IMAGE_COUNT = 2;
    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final String IMAGE_URL_PREFIX = "/api/festivals/images/";

    @Value("${festival.image.saved-path:./savedimage}")
    private String savedImagePath;

    //대표 이미지(썸네일, 0~1장)와 본문 이미지(0~2장)를 함께 업로드한다. 등록 전 미리보기 URL만 받아두고,
    //실제 등록(FestivalRequestDto)에는 이 URL을 그대로 실어 보낸다.
    public FestivalImageUploadResponseDto upload(String role, MultipartFile thumbnail, List<MultipartFile> detailImages) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_HOST_ROLE);
        }

        List<MultipartFile> thumbnails = thumbnail == null || thumbnail.isEmpty() ? List.of() : List.of(thumbnail);
        List<MultipartFile> details = detailImages == null ? List.of() : detailImages;

        if (thumbnails.size() > MAX_THUMBNAIL_COUNT) {
            throw new ApiException(FestivalErrorCode.INVALID_IMAGE_COUNT);
        }
        if (details.size() > MAX_DETAIL_IMAGE_COUNT) {
            throw new ApiException(FestivalErrorCode.INVALID_DETAIL_IMAGE_COUNT);
        }

        String thumbnailUrl = thumbnails.isEmpty() ? null : storeSingle(thumbnails.get(0));
        List<String> detailUrls = details.stream().map(this::storeSingle).toList();

        return new FestivalImageUploadResponseDto(thumbnailUrl, detailUrls);
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
