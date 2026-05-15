package com.profitsaathi.seller.shipping;

import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
public class ShippingVendorSeeder implements CommandLineRunner {

    private final ShippingVendorRepository repository;

    @Override
    public void run(String... args) {
        for (Map<String, String> seed : SEED_DATA) {
            String code = seed.get("code");
            if (repository.findByCode(code).isPresent()) continue;
            ShippingVendor v = new ShippingVendor();
            v.setCode(code);
            v.setName(seed.get("name"));
            v.setTrackingUrlTemplate(seed.get("trackingUrlTemplate"));
            repository.save(v);
        }
    }

    private static final List<Map<String, String>> SEED_DATA = List.of(
            Map.of("code", "INDIA_POST", "name", "India Post",
                    "trackingUrlTemplate",
                    "https://www.indiapost.gov.in/_layouts/15/dop.portal.tracking/trackconsignment.aspx?LocationId=0&articleNumber={trackingId}"),
            Map.of("code", "BLUE_DART", "name", "Blue Dart",
                    "trackingUrlTemplate",
                    "https://www.bluedart.com/tracking?trackFor=0&trackNo={trackingId}"),
            Map.of("code", "DTDC", "name", "DTDC",
                    "trackingUrlTemplate",
                    "https://www.dtdc.in/tracking/tracking_results.asp?strCnno={trackingId}"),
            Map.of("code", "DELHIVERY", "name", "Delhivery",
                    "trackingUrlTemplate",
                    "https://www.delhivery.com/tracking/{trackingId}"),
            Map.of("code", "DHL", "name", "DHL",
                    "trackingUrlTemplate",
                    "https://www.dhl.com/in-en/home/tracking/tracking-express.html?submit=1&tracking-id={trackingId}"),
            Map.of("code", "FEDEX", "name", "FedEx",
                    "trackingUrlTemplate",
                    "https://www.fedex.com/fedextrack/?trknbr={trackingId}"),
            Map.of("code", "ARAMEX", "name", "Aramex",
                    "trackingUrlTemplate",
                    "https://www.aramex.com/in/en/track/results?ShipmentNumber={trackingId}"),
            Map.of("code", "EKART", "name", "Ekart",
                    "trackingUrlTemplate",
                    "https://ekartlogistics.com/track/{trackingId}"),
            Map.of("code", "ECOM_EXPRESS", "name", "Ecom Express",
                    "trackingUrlTemplate",
                    "https://www.ecomexpress.in/tracking/?awb_field={trackingId}"),
            Map.of("code", "XPRESSBEES", "name", "Xpressbees",
                    "trackingUrlTemplate",
                    "https://www.xpressbees.com/track?trackid={trackingId}"),
            Map.of("code", "GATI", "name", "Gati",
                    "trackingUrlTemplate",
                    "https://www.gati.com/track-shipment?docketno={trackingId}"),
            Map.of("code", "SHIPROCKET", "name", "Shiprocket",
                    "trackingUrlTemplate",
                    "https://shiprocket.co/tracking/{trackingId}"),
            Map.of("code", "PROFESSIONAL_COURIERS", "name", "The Professional Couriers",
                    "trackingUrlTemplate",
                    "https://www.tpcindia.com/Tracking2014.aspx?id={trackingId}"),
            Map.of("code", "TRACKON", "name", "Trackon",
                    "trackingUrlTemplate",
                    "https://trackon.in/Tracking/Index?awb={trackingId}"),
            Map.of("code", "SHADOWFAX", "name", "Shadowfax",
                    "trackingUrlTemplate",
                    "https://tracker.shadowfax.in/?awbs={trackingId}")
    );
}
