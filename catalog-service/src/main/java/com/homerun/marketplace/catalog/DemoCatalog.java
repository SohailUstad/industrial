package com.homerun.marketplace.catalog;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
public class DemoCatalog {
    @Bean
    ApplicationRunner seed(ProductRepository products) {
        return args -> {
            if (!products.findAll(true).isEmpty()) return;
            products.create(new Product.Input("NAIL-50", "Steel nails 50 mm", "Fasteners",
                    "General purpose steel nails for carpentry and site work. Pack of 100.", "pack", 12900, null, true));
            products.create(new Product.Input("WELD-6013", "E6013 welding rods", "Welding",
                    "3.15 mm mild steel welding electrodes for everyday fabrication. 5 kg box.", "box", 89900, null, true));
            products.create(new Product.Input("PAINT-INT-W", "Interior wall paint", "Paint",
                    "Washable white interior emulsion paint. 1 litre tin.", "tin", 34900, null, true));
            products.create(new Product.Input("DISC-4IN", "Cutting discs 4 inch", "Tools",
                    "High durability metal cutting discs for angle grinders. Pack of 10.", "pack", 29900, null, true));
            products.create(new Product.Input("BOLT-M8", "M8 hex bolts", "Fasteners",
                    "Zinc plated M8 hex bolts for general industrial assembly. Pack of 25.", "pack", 17900, null, true));
            products.create(new Product.Input("GLOVE-GRIP", "Grip work gloves", "Safety",
                    "Reusable coated gloves for handling tools and materials. One pair.", "pair", 9900, null, true));
            products.create(new Product.Input("DRILL-SET", "HSS drill bit set", "Tools",
                    "13 piece high speed steel drill bit set for metal and wood.", "set", 54900, null, true));
            products.create(new Product.Input("ADH-500", "Construction adhesive", "Adhesives",
                    "Multipurpose construction adhesive for interior fixing. 500 ml tube.", "tube", 23900, null, true));
        };
    }
}
