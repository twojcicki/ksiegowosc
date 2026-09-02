package pl.tw.ksiegowosc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import pl.tw.ksiegowosc.dto.AllegroOfferDto;
import pl.tw.ksiegowosc.service.AllegroOffersService;

@RestController
@RequestMapping("/api/allegro/offers")
@Tag(name = "Allegro — oferty", description = "Aktywne oferty sprzedawcy z Allegro Sandbox")
public class AllegroOffersController {

    private final AllegroOffersService offersService;

    public AllegroOffersController(AllegroOffersService offersService) {
        this.offersService = offersService;
    }

    @GetMapping
    @Operation(summary = "Lista ofert", description = "Zwraca aktywne oferty sprzedawcy z Allegro Sandbox.")
    public List<AllegroOfferDto> getOffers(
            @Parameter(description = "Indeks pierwszej oferty") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Maksymalna liczba ofert (1–1000)") @RequestParam(defaultValue = "100") int limit,
            @Parameter(description = "Filtr statusu publikacji, np. ACTIVE") @RequestParam(required = false)
            String publicationStatus) {
        return offersService.getOffers(offset, limit, publicationStatus);
    }
}
