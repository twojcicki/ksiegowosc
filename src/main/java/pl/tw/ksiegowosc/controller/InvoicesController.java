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
import pl.tw.ksiegowosc.dto.SalesInvoiceDto;
import pl.tw.ksiegowosc.service.InvoicesService;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Faktury", description = "Pobieranie faktur sprzedaży z Merit Aktiva")
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
}
