package com.profitsaathi.config;

import com.profitsaathi.seller.aichat.SaathiKillSwitchInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves uploaded product images and payment proofs as static resources,
 * and wires the Saathi AI kill-switch interceptor.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Value("${product.image.upload-dir}")
    private String productImageDir;

    @Value("${file.upload-dir}")
    private String paymentDir;

    private final SaathiKillSwitchInterceptor saathiKillSwitch;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path products = Paths.get(productImageDir).toAbsolutePath().normalize();
        Path payments = Paths.get(paymentDir).toAbsolutePath().normalize();

        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations("file:" + products + "/");

        registry.addResourceHandler("/uploads/payment/**")
                .addResourceLocations("file:" + payments + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Single global gate for the Saathi AI surface. Flip
        // `ai.saathi.enabled=false` (or AI_SAATHI_ENABLED=false) to instantly
        // 503 every chat/image endpoint without a redeploy.
        registry.addInterceptor(saathiKillSwitch)
                .addPathPatterns("/api/v1/ai/saathi/**");
    }
}
