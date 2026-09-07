package org.example.festivalservice.common;

import java.nio.file.Path;
import org.example.festivalservice.domain.festival.FestivalImageUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** FestivalImageUploadService가 SAVED_IMAGE 경로에 저장한 파일을 정적 리소스로 공개한다. */
@Configuration
public class ImageStaticResourceConfig implements WebMvcConfigurer {

    @Value("${festival.image.saved-path:./savedimage}")
    private String savedImagePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Path.of(savedImagePath).toAbsolutePath() + "/";
        registry.addResourceHandler(FestivalImageUploadService.IMAGE_URL_PREFIX + "**")
                .addResourceLocations(location);
    }
}
