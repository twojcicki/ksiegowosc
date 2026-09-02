package pl.tw.ksiegowosc.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.service.AllegroOrdersService;

@RestController
@RequestMapping("/api/allegro/sold-items")
@Tag(name = "Allegro — sprzedane", description = "Sprzedane pozycje z zamówień Allegro Sandbox")
public class AllegroOrdersController {

    private final AllegroOrdersService ordersService;

    public AllegroOrdersController(AllegroOrdersService ordersService) {
        this.ordersService = ordersService;
    }

    @GetMapping
    @Operation(
            summary = "Lista sprzedanych pozycji",
            description = "Zwraca pozycje z zamówień Allegro Sandbox w podanym zakresie dat zakupu.")
    public List<AllegroSoldItemDto> getSoldItems(
            @Parameter(description = "Początek zakresu (yyyy-MM-dd)", required = true, example = "2026-01-01")
            @RequestParam LocalDate from,
            @Parameter(description = "Koniec zakresu (yyyy-MM-dd)", required = true, example = "2026-01-31")
            @RequestParam LocalDate to,
            @Parameter(description = "Indeks pierwszej pozycji") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Maksymalna liczba zamówień (1–100)") @RequestParam(defaultValue = "100") int limit) {
        return ordersService.getSoldItems(from, to, offset, limit);
    }
}
