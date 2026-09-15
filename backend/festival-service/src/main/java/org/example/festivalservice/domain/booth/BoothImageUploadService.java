package org.example.festivalservice.domain.booth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 부스 등록 전 대표 이미지 1장을 먼저 저장하고 URL만 돌려준다. FestivalImageUploadService와 같은 패턴이지만
 * 부스는 이미지가 1장뿐이라 훨씬 단순하다. Booth 엔티티·DB는 전혀 알지 못한다 — 등록 시점에 이 URL을
 * BoothRequestDto.imageUrl로 그대로 실어 보내면 BoothService가 그때 저장한다.
 */
@Service
@RequiredArgsConstructor
public class BoothImageUploadService {

    private static final String STOREHOST_ROLE = "STOREHOST";
    private static final long MAX_IMAGE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final String IMAGE_URL_PREFIX = "/api/booths/images/";

    //festival.image.saved-path 하위의 booths 폴더에 따로 저장해 페스티벌 이미지와 섞이지 않게 한다.
    @Value("${festival.image.saved-path:./savedimage}")
    private String savedImagePath;

    public BoothImageUploadResponseDto upload(String role, MultipartFile image) {
        if (!STOREHOST_ROLE.equals(role)) {
            throw new ApiException(BoothErrorCode.FORBIDDEN_STOREHOST_ROLE);
        }
        if (image == null || image.isEmpty()) {
            return new BoothImageUploadResponseDto(null);
        }
        return new BoothImageUploadResponseDto(storeSingle(image));
    }

    private String storeSingle(MultipartFile file) {
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new ApiException(BoothErrorCode.INVALID_IMAGE_SIZE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApiException(BoothErrorCode.INVALID_IMAGE_TYPE);
        }

        String filename = UUID.randomUUID() + extractExtension(file.getOriginalFilename());
        try {
            Path directory = Path.of(savedImagePath, "booths");
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(filename));
        } catch (IOException e) {
            throw new ApiException(BoothErrorCode.IMAGE_UPLOAD_FAILED);
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
