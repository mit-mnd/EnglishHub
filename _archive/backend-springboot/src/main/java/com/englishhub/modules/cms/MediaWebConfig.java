package com.englishhub.modules.cms;

import com.englishhub.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Phục vụ media admin tải lên tại /media/uploads/** (hỗ trợ HTTP Range để tua audio/video).
 * Tên file là UUID nên có thể cache lâu. Bản ghi âm của học viên nằm ở thư mục khác, không public.
 */
@Configuration
@RequiredArgsConstructor
public class MediaWebConfig implements WebMvcConfigurer {

    private final AppProperties props;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Path.of(props.storage().dir()).toAbsolutePath().normalize().resolve(AdminMediaController.FOLDER);
        registry.addResourceHandler(AdminMediaController.PUBLIC_PREFIX + "**")
                .addResourceLocations(dir.toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
