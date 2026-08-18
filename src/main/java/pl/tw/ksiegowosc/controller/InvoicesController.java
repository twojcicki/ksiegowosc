package pl.tw.ksiegowosc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
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

    @GetMapping("/yesterday")
    @Operation(summary = "Faktury z wczoraj", description = "Zwraca listę faktur sprzedaży z wczorajszego dnia według daty dokumentu (strefa Europe/Warsaw).")
    public List<SalesInvoiceDto> getYesterdaysInvoices() {
        return invoicesService.getYesterdaysInvoices();
    }
}
