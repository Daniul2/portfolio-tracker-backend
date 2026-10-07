package com.kodilla.portfolio.controller;

import com.kodilla.portfolio.dto.AssetDtos.AssetRequest;
import com.kodilla.portfolio.dto.AssetDtos.AssetResponse;
import com.kodilla.portfolio.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping
    public List<AssetResponse> findAll(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        return activeOnly ? assetService.findActive() : assetService.findAll();
    }

    @GetMapping("/{id}")
    public AssetResponse findById(@PathVariable Long id) {
        return assetService.findById(id);
    }

    @GetMapping("/by-symbol/{symbol}")
    public AssetResponse findBySymbol(@PathVariable String symbol) {
        return assetService.findBySymbol(symbol);
    }

    @PostMapping
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody AssetRequest request) {
        AssetResponse created = assetService.create(request);
        return ResponseEntity.created(URI.create("/v1/assets/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public AssetResponse update(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return assetService.update(id, request);
    }

    /** Partial update: only toggles whether the scheduler prices this asset. */
    @PatchMapping("/{id}/active")
    public AssetResponse setActive(@PathVariable Long id, @RequestParam boolean value) {
        return assetService.setActive(id, value);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        assetService.delete(id);
    }
}
