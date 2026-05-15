package com.profitsaathi.Geo.Controller;


import com.profitsaathi.Geo.DTO.LocationResponse;
import com.profitsaathi.Geo.service.GeoCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final GeoCodeService geoCodeService;

    @GetMapping("/reverse")
    public LocationResponse reverseGeocode(
            @RequestParam double lat,
            @RequestParam double lon
    ) {

        return geoCodeService.getLocation(lat, lon);
    }
}