package pl.tw.ksiegowosc.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import pl.tw.ksiegowosc.dto.CustomerDto;
import pl.tw.ksiegowosc.service.CustomersService;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Klienci", description = "Klienci z Merit Aktiva")
public class CustomersController {

    private final CustomersService customersService;

    public CustomersController(CustomersService customersService) {
        this.customersService = customersService;
    }

    @GetMapping
    @Operation(
            summary = "Lista klientów",
            description = "Zwraca listę klientów z Merit. Opcjonalny parametr name filtruje po nazwie (dopasowanie częściowe).")
    public List<CustomerDto> getCustomers(
            @Parameter(description = "Fragment nazwy klienta (broad match)")
            @RequestParam(required = false) String name) {
        return customersService.getCustomers(name);
    }
}
