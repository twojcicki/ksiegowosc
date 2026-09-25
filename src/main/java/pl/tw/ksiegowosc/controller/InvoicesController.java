package pl.tw.ksiegowosc.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceResponse;
import pl.tw.ksiegowosc.dto.SalesInvoiceDetailsDto;
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.dto.SendInvoiceEmailResponse;
import pl.tw.ksiegowosc.service.InvoicesService;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Faktury", description = "Faktury sprzedaży z Merit Aktiva")
public class InvoicesController {

    private final InvoicesService invoicesService;

    public InvoicesController(InvoicesService invoicesService) {
        this.invoicesService = invoicesService;
    }

    @GetMapping
    @Operation(
            summary = "Faktury z zakresu dat",
            description = "Zwraca listę faktur sprzedaży z podanego zakresu według daty dokumentu. "
                    + "Zakres nie może przekraczać 3 miesięcy (limit API Merit).")
    public List<SalesInvoiceDto> getInvoices(
            @Parameter(description = "Początek zakresu (yyyy-MM-dd)", required = true, example = "2026-01-01")
            @RequestParam LocalDate from,
            @Parameter(description = "Koniec zakresu (yyyy-MM-dd)", required = true, example = "2026-01-31")
            @RequestParam LocalDate to) {
        return invoicesService.getInvoices(from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Utwórz fakturę sprzedaży",
            description = "Tworzy fakturę sprzedaży w Merit Aktiva dla istniejącego klienta. "
                    + "Wymagany jest m.in. komentarz górny (HComment).")
    public CreateInvoiceResponse createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        return invoicesService.createInvoice(request);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Szczegóły faktury",
            description = "Zwraca pełne szczegóły faktury sprzedaży z Merit (nagłówek, pozycje, płatności). "
                    + "Identyfikator to SIHId z listy faktur.")
    public SalesInvoiceDetailsDto getInvoiceDetails(
            @Parameter(description = "SIHId faktury", required = true)
            @PathVariable String id,
            @Parameter(description = "Czy dołączyć załącznik PDF w base64")
            @RequestParam(defaultValue = "false") boolean addAttachment) {
        return invoicesService.getInvoiceDetails(id, addAttachment);
    }

    @PostMapping("/{id}/email")
    @Operation(
            summary = "Wyślij fakturę e-mailem",
            description = "Wysyła fakturę sprzedaży na adres e-mail klienta zapisany w Merit Aktiva. "
                    + "Identyfikator to SIHId z listy faktur.")
    public SendInvoiceEmailResponse sendInvoiceByEmail(
            @Parameter(description = "SIHId faktury", required = true)
            @PathVariable String id,
            @Parameter(description = "true = dokument bez cen (WZ)")
            @RequestParam(defaultValue = "false") boolean delivNote) {
        return invoicesService.sendInvoiceByEmail(id, delivNote);
    }
}
