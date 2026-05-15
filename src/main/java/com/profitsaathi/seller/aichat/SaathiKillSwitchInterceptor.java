package com.profitsaathi.seller.aichat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Short-circuits every {@code /api/v1/ai/saathi/**} request when the feature
 * flag is off, returning a clean 503 with a JSON error body that the UI can
 * recognise and surface.
 *
 * Lives separate from {@link SaathiKillSwitch} so the bean itself stays a
 * simple value holder (easier to unit-test); the interceptor is the Spring
 * MVC glue that wires it into request handling. Registered in
 * {@code WebConfig.addInterceptors}.
 */
@Component
@RequiredArgsConstructor
public class SaathiKillSwitchInterceptor implements HandlerInterceptor {

    private final SaathiKillSwitch killSwitch;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (killSwitch.isEnabled()) return true;

        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"message\":\"Saathi AI is temporarily paused by ProfitSaathi. Please try again later.\","
                + "\"code\":\"SERVICE_PAUSED\"}");
        return false;
    }
}
