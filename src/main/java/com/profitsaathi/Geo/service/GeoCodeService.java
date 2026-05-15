package com.profitsaathi.Geo.service;



import com.fasterxml.jackson.databind.JsonNode;
import com.profitsaathi.Geo.Config.GeoCodeProperties;
import com.profitsaathi.Geo.DTO.LocationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class GeoCodeService {

    private final GeoCodeProperties properties;

    private final WebClient webClient = WebClient.builder().build();

    public LocationResponse getLocation(double lat, double lon) {

        String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("geocode.maps.co")
                        .path("/reverse")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("api_key", properties.getApiKey())
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {

            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(response);

            JsonNode address = root.path("address");

            /*
             Payload may change depending on location.
             Sometimes city is:
             - city
             - town
             - village
             - county
             */

            String city =
                    getSafe(address, "city",
                            getSafe(address, "town",
                                    getSafe(address, "village",
                                            getSafe(address, "county", null))));

            String state = getSafe(address, "state", null);

            String country = getSafe(address, "country", null);

            String postcode = getSafe(address, "postcode", null);

            String displayName = root.path("display_name").asText();

            return new LocationResponse(
                    city,
                    state,
                    country,
                    postcode,
                    displayName
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse geocode response", e);
        }
    }

    private String getSafe(JsonNode node, String field, String defaultValue) {

        JsonNode value = node.get(field);

        return value != null && !value.isNull()
                ? value.asText()
                : defaultValue;
    }
}
