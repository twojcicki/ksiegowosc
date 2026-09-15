package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import pl.tw.ksiegowosc.dto.BuyerBilling;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroLineItem;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class AllegroInvoiceMapper {

    @Autowired
    protected AllegroToMeritInvoiceBuilder invoiceBuilder;

    @Mapping(target = "currencyCode", constant = "PLN")
    @Mapping(target = "salesInvLang", constant = "PL")
    @Mapping(target = "vatRegNo", source = "vatRegNo", qualifiedByName = "blankToNull")
    @Mapping(target = "address", source = "address", qualifiedByName = "blankToNull")
    @Mapping(target = "city", source = "city", qualifiedByName = "blankToNull")
    @Mapping(target = "postalCode", source = "postalCode", qualifiedByName = "blankToNull")
    @Mapping(target = "email", source = "email", qualifiedByName = "blankToNull")
    public abstract MeritCreateCustomerRequest toCustomerRequest(BuyerBilling billing);

    @Named("blankToNull")
    protected String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public CreateInvoiceRequest toCreateInvoiceRequest(
            AllegroCheckoutForm form,
            AllegroInvoiceMappingContext context) {
        return invoiceBuilder.toCreateInvoiceRequest(form, context);
    }

    public String buildInvoiceNo(String prefix, int sequenceNumber, LocalDate docDate) {
        return AllegroInvoiceMappingSupport.buildInvoiceNo(prefix, sequenceNumber, docDate);
    }

    public BigDecimal toNet(BigDecimal gross, BigDecimal vatRate) {
        return AllegroInvoiceMappingSupport.toNet(gross, vatRate);
    }

    public Instant earliestBoughtAt(List<AllegroLineItem> lineItems) {
        return invoiceBuilder.earliestBoughtAt(lineItems);
    }
}
