package org.example.festivalservice.festival;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.booth.BoothErrorCode;
import org.example.festivalservice.domain.booth.BoothImageUploadService;
import org.example.festivalservice.domain.festival.FestivalErrorCode;
import org.example.festivalservice.domain.festival.FestivalImageUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/** 실제 래스터 이미지만 기존 경로로 저장되고 위장 파일은 저장 전에 거절되는지 검증한다. */
class ImageUploadValidationTest {

    @TempDir Path directory;
    private FestivalImageUploadService festivals;
    private BoothImageUploadService booths;

    @BeforeEach
    void setUp() {
        festivals = new FestivalImageUploadService();
        booths = new BoothImageUploadService();
        ReflectionTestUtils.setField(festivals, "savedImagePath", directory.toString());
        ReflectionTestUtils.setField(booths, "savedImagePath", directory.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "jpeg", "png", "gif", "PNG"})
    void 정상_이미지는_기존_URL과_원본_바이트로_저장한다(String extension) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), extension, output)).isTrue();
        assertStored(extension, output.toByteArray());
    }

    @Test
    void 정상_WebP도_디코딩한_뒤_원본으로_저장한다() throws Exception {
        //직접 생성한 2×2 단색 이미지로 외부 파일이나 네트워크 없이 WebP 지원을 검증한다.
        byte[] bytes = Base64.getDecoder().decode(
                "UklGRjwAAABXRUJQVlA4IDAAAAAQAgCdASoCAAIAAUAmJaACdLoB+AH4AAPIAP7xTa/9lXwmR/6pn/1OEuQY+hQAAAA=");
        assertStored("webp", bytes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"svg", "html", "jpg", "png", "webp", ""})
    void 이미지가_아닌_바이트는_확장자나_MIME을_위조해도_거부한다(String extension) throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "fake." + extension, "image/png",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                        .getBytes(StandardCharsets.UTF_8));
        assertRejected(file);
        try (var saved = Files.list(directory)) {
            assertThat(saved).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "svg"})
    void 실제_이미지라도_확장자가_다르면_거부한다(String extension) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        assertRejected(new MockMultipartFile("image", "fake." + extension, "image/png", output.toByteArray()));
    }

    private void assertStored(String extension, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "valid." + extension, "image/" + extension, bytes);
        String festivalUrl = festivals.upload("HOST", file, List.of()).thumbnailImageUrl();
        String boothUrl = booths.upload("STOREHOST", file).imageUrl();
        assertThat(festivalUrl).startsWith("/api/festivals/images/").endsWith("." + extension);
        assertThat(boothUrl).startsWith("/api/booths/images/").endsWith("." + extension);
        assertThat(Files.readAllBytes(directory.resolve(festivalUrl.substring(festivalUrl.lastIndexOf('/') + 1))))
                .isEqualTo(bytes);
        assertThat(Files.readAllBytes(directory.resolve("booths").resolve(boothUrl.substring(boothUrl.lastIndexOf('/') + 1))))
                .isEqualTo(bytes);
    }

    private void assertRejected(MockMultipartFile file) {
        assertThatThrownBy(() -> festivals.upload("HOST", file, List.of())).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", FestivalErrorCode.INVALID_IMAGE_TYPE);
        assertThatThrownBy(() -> booths.upload("STOREHOST", file)).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", BoothErrorCode.INVALID_IMAGE_TYPE);
    }
}
