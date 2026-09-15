package pl.tw.ksiegowosc.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import pl.tw.ksiegowosc.dto.AllegroSoldItemDto;
import pl.tw.ksiegowosc.dto.IssueAllegroInvoiceRequest;
import pl.tw.ksiegowosc.dto.IssueAllegroInvoiceResponse;
import pl.tw.ksiegowosc.service.AllegroInvoiceService;
import pl.tw.ksiegowosc.service.AllegroOrdersService;

@RestController
@RequestMapping("/api/allegro/sold-items")
@Tag(name = "Allegro — sprzedane", description = "Zamówienia Allegro Sandbox i wystawianie faktur")
public class AllegroOrdersController {

    private final AllegroOrdersService ordersService;
    private final AllegroInvoiceService invoiceService;

    public AllegroOrdersController(AllegroOrdersService ordersService, AllegroInvoiceService invoiceService) {
        this.ordersService = ordersService;
        this.invoiceService = invoiceService;
    }

    @GetMapping
    @Operation(
            summary = "Lista sprzedanych zamówień",
            description = "Zwraca zamówienia Allegro Sandbox w podanym zakresie dat zakupu (1 wiersz = 1 checkout-form).")
    public List<AllegroSoldItemDto> getSoldItems(
            @Parameter(description = "Początek zakresu (yyyy-MM-dd)", required = true, example = "2026-01-01")
            @RequestParam LocalDate from,
            @Parameter(description = "Koniec zakresu (yyyy-MM-dd)", required = true, example = "2026-01-31")
            @RequestParam LocalDate to,
            @Parameter(description = "Indeks pierwszej pozycji") @RequestParam(defaultValue = "0") int offset,
            @Parameter(description = "Maksymalna liczba zamówień (1–100)") @RequestParam(defaultValue = "100") int limit) {
        return ordersService.getSoldItems(from, to, offset, limit);
    }

    @PostMapping("/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Wystaw fakturę dla zamówienia Allegro",
            description = "Pobiera dane zamówienia i kupującego z Allegro, tworzy/znajduje klienta w Merit "
                    + "i wystawia fakturę ze wszystkimi pozycjami zamówienia.")
    public IssueAllegroInvoiceResponse issueInvoice(@Valid @RequestBody IssueAllegroInvoiceRequest request) {
        return invoiceService.issueInvoice(request.accountId(), request.orderId());
    }
}
