package org.example.festivalservice.common;

import java.nio.file.Path;
import org.example.festivalservice.domain.booth.BoothImageUploadService;
import org.example.festivalservice.domain.festival.FestivalImageUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** FestivalImageUploadService·BoothImageUploadService가 SAVED_IMAGE 경로에 저장한 파일을 정적 리소스로 공개한다. */
@Configuration
public class ImageStaticResourceConfig implements WebMvcConfigurer {

    @Value("${festival.image.saved-path:./savedimage}")
    private String savedImagePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Path.of(savedImagePath).toAbsolutePath() + "/";
        registry.addResourceHandler(FestivalImageUploadService.IMAGE_URL_PREFIX + "**")
                .addResourceLocations(location);
        //부스 이미지는 savedImagePath/booths 하위에 저장하므로(BoothImageUploadService), 리소스 위치도 같이 맞춘다.
        registry.addResourceHandler(BoothImageUploadService.IMAGE_URL_PREFIX + "**")
                .addResourceLocations(location + "booths/");
    }
}
