package com.profitsaathi.seller.shipping;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ShippingVendorService {

    private final ShippingVendorRepository repository;

    public List<Map<String, Object>> listAll() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(ShippingVendorService::toMap)
                .collect(Collectors.toList());
    }

    public String resolveTrackingUrl(String code, String trackingId) {
        if (code == null || trackingId == null || trackingId.isBlank()) return null;
        return repository.findByCode(code)
                .map(ShippingVendor::getTrackingUrlTemplate)
                .filter(t -> t != null && t.contains("{trackingId}"))
                .map(t -> t.replace("{trackingId}", trackingId.trim()))
                .orElse(null);
    }

    public Map<String, Object> getOrThrow(String code) {
        return repository.findByCode(code)
                .map(ShippingVendorService::toMap)
                .orElseThrow(() -> new RuntimeException("Vendor not found: " + code));
    }

    private static Map<String, Object> toMap(ShippingVendor v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", v.getId());
        map.put("code", v.getCode());
        map.put("name", v.getName());
        map.put("trackingUrlTemplate", v.getTrackingUrlTemplate());
        return map;
    }
}
