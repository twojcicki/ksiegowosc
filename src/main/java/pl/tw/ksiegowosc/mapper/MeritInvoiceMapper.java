package pl.tw.ksiegowosc.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import pl.tw.ksiegowosc.dto.CreateInvoiceLineRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.CreateInvoiceTaxAmountRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceCustomer;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceItem;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRow;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceTaxAmount;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MeritInvoiceMapper {

    @Mapping(target = "customer", source = "customerId", qualifiedByName = "toCustomer")
    @Mapping(target = "accountingDoc", constant = "1")
    @Mapping(target = "docDate", source = "docDate", qualifiedByName = "toMeritDate")
    @Mapping(target = "dueDate", source = "dueDate", qualifiedByName = "toMeritDate")
    @Mapping(target = "invoiceRow", source = "lines")
    @Mapping(target = "taxAmount", source = "taxAmounts")
    @Mapping(target = "hComment", source = "headerComment")
    @Mapping(target = "fComment", source = "footerComment")
    MeritCreateInvoiceRequest toMeritRequest(CreateInvoiceRequest request);

    @Mapping(target = "item", source = ".")
    @Mapping(target = "quantity", source = "quantity")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "taxId", source = "taxId")
    MeritCreateInvoiceRow toRow(CreateInvoiceLineRequest line);

    @Mapping(target = "code", source = "itemCode")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "type", source = "itemType")
    @Mapping(target = "uomName", source = "uomName")
    MeritCreateInvoiceItem toItem(CreateInvoiceLineRequest line);

    MeritCreateInvoiceTaxAmount toTaxAmount(CreateInvoiceTaxAmountRequest tax);

    @Named("toCustomer")
    default MeritCreateInvoiceCustomer toCustomer(String customerId) {
        return new MeritCreateInvoiceCustomer(customerId);
    }

    @Named("toMeritDate")
    default String toMeritDate(LocalDate date) {
        return date.format(DateTimeFormatter.BASIC_ISO_DATE) + "000000";
    }
}
