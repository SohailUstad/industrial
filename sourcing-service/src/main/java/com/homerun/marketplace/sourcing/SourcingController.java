package com.homerun.marketplace.sourcing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class SourcingController {
    private final SourcingRepository data;
    public SourcingController(SourcingRepository data) { this.data = data; }

    @GetMapping("/vendors")
    public List<Vendor> vendors() { return data.vendors(); }
    @PostMapping("/vendors")
    @ResponseStatus(HttpStatus.CREATED)
    public Vendor createVendor(@Valid @RequestBody Vendor.Input input) { return data.createVendor(input); }
    @PutMapping("/vendors/{id}")
    public Vendor updateVendor(@PathVariable String id, @Valid @RequestBody Vendor.Input input) {
        if (!data.updateVendor(id, input)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return data.vendor(id).orElseThrow();
    }
    @DeleteMapping("/vendors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVendor(@PathVariable String id) {
        if (!data.deleteVendor(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @GetMapping("/offers")
    public List<VendorOffer> offers(@RequestParam(required = false) String productId) { return data.offers(productId); }
    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorOffer createOffer(@Valid @RequestBody VendorOffer.Input input) { return data.createOffer(input); }
    @PutMapping("/offers/{id}")
    public VendorOffer updateOffer(@PathVariable String id, @Valid @RequestBody VendorOffer.Input input) {
        if (!data.updateOffer(id, input)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return data.offer(id).orElseThrow();
    }
    @DeleteMapping("/offers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOffer(@PathVariable String id) {
        if (!data.deleteOffer(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
