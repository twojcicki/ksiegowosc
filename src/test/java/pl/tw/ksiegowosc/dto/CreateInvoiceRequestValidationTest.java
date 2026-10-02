package pl.tw.ksiegowosc.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CreateInvoiceRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldAcceptValidRequest() {
        Set<ConstraintViolation<CreateInvoiceRequest>> violations = validator.validate(validRequest());

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAcceptNullHeaderComment() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                "FV/2026/01/01",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15),
                null,
                "PLN",
                null,
                "Komentarz dolny",
                new BigDecimal("100.00"),
                null,
                List.of(validLine()),
                List.of(validTax()),
                null);

        Set<ConstraintViolation<CreateInvoiceRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldAcceptNullFooterComment() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                "FV/2026/01/01",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15),
                null,
                "PLN",
                "Komentarz górny",
                null,
                new BigDecimal("100.00"),
                null,
                List.of(validLine()),
                List.of(validTax()),
                null);

        Set<ConstraintViolation<CreateInvoiceRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectEmptyLines() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                "FV/2026/01/01",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15),
                null,
                "PLN",
                "Komentarz górny",
                "Komentarz dolny",
                new BigDecimal("100.00"),
                null,
                Collections.emptyList(),
                List.of(validTax()),
                null);

        Set<ConstraintViolation<CreateInvoiceRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> "lines".equals(v.getPropertyPath().toString()));
    }

    private static CreateInvoiceRequest validRequest() {
        return new CreateInvoiceRequest(
                "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                "FV/2026/01/01",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15),
                null,
                "PLN",
                "Komentarz górny",
                "Komentarz dolny",
                new BigDecimal("100.00"),
                null,
                List.of(validLine()),
                List.of(validTax()),
                null);
    }

    private static CreateInvoiceLineRequest validLine() {
        return new CreateInvoiceLineRequest(
                "USLUGA",
                "Usługa",
                2,
                new BigDecimal("1"),
                new BigDecimal("100.00"),
                "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                null);
    }

    private static CreateInvoiceTaxAmountRequest validTax() {
        return new CreateInvoiceTaxAmountRequest(
                "665f01a4-357a-4a6b-a565-2f17e6e1da13",
                new BigDecimal("23.00"));
    }
}
