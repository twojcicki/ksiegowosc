package pl.tw.ksiegowosc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pl.tw.ksiegowosc.dto.MeritUnitDto;
import pl.tw.ksiegowosc.service.UnitsService;

@RestController
@RequestMapping("/api/units")
@Tag(name = "Jednostki miary", description = "Jednostki miary z Merit Aktiva")
public class UnitsController {

    private final UnitsService unitsService;

    public UnitsController(UnitsService unitsService) {
        this.unitsService = unitsService;
    }

    @GetMapping
    @Operation(
            summary = "Lista jednostek miary",
            description = "Zwraca listę jednostek miary z Merit (getunits).")
    public List<MeritUnitDto> getUnits() {
        return unitsService.listUnits();
    }
}
