package ru.projectx.contracts.controller;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MultipartWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MultipartInterceptor()).addPathPatterns(
                "/internal/v1/requests/*/participants/*/attachments",
                "/internal/v1/policies/*/family-members/*/consents");
    }
}
