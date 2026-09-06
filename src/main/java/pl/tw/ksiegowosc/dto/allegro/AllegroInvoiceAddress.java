package pl.tw.ksiegowosc.dto.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AllegroInvoiceAddress(
        String street,
        String city,
        String zipCode,
        String countryCode,
        AllegroInvoiceCompany company,
        AllegroNaturalPerson naturalPerson) {
}
