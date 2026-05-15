package com.profitsaathi.Geo.Config;

import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Data
@Configuration
public class GeoCodeProperties {

    @Value("${geocode.api.key}")
    private String apiKey;

    @Value("${geocode.base.url}")
    private String baseUrl;
}