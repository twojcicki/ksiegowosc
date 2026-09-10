package pl.tw.ksiegowosc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.service.TaxesService;

@RestController
@RequestMapping("/api/taxes")
@Tag(name = "Stawki VAT", description = "Stawki VAT z Merit Aktiva")
public class TaxesController {

    private final TaxesService taxesService;

    public TaxesController(TaxesService taxesService) {
        this.taxesService = taxesService;
    }

    @GetMapping
    @Operation(
            summary = "Lista stawek VAT",
            description = "Zwraca listę stawek VAT z Merit (gettaxes).")
    public List<MeritTaxDto> getTaxes() {
        return taxesService.listTaxes();
    }
}
