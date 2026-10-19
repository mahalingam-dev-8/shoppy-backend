package com.shoppy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * The frontend uploads images to {@code /Products/{id}/image} (capital P). Express matched
     * routes case-insensitively, so the NestJS version accepted it; Spring is case-sensitive by
     * default, so match the old behaviour rather than break the upload.
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        PathPatternParser parser = new PathPatternParser();
        parser.setCaseSensitive(false);
        configurer.setPatternParser(parser);
    }
}
