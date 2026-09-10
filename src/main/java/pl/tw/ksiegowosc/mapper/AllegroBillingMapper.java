package pl.tw.ksiegowosc.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import pl.tw.ksiegowosc.dto.BuyerBilling;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoice;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoiceAddress;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoiceCompany;
import pl.tw.ksiegowosc.dto.allegro.AllegroNaturalPerson;
import pl.tw.ksiegowosc.dto.allegro.AllegroTaxId;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AllegroBillingMapper {

    default BuyerBilling toBuyerBilling(AllegroCheckoutForm form) {
        AllegroBuyer buyer = form.buyer();
        String login = buyer == null ? null : buyer.login();
        String email = buyer == null ? null : buyer.email();

        AllegroInvoice invoice = form.invoice();
        AllegroInvoiceAddress address = invoice == null ? null : invoice.address();
        AllegroInvoiceCompany company = address == null ? null : address.company();
        AllegroNaturalPerson person = address == null ? null : address.naturalPerson();

        String vatRegNo = resolveVatRegNo(company);
        String name;
        boolean notTdCustomer;
        if (company != null && company.name() != null && !company.name().isBlank()) {
            name = company.name().trim();
            notTdCustomer = vatRegNo == null || vatRegNo.isBlank();
        } else if (person != null) {
            name = ((person.firstName() == null ? "" : person.firstName().trim()) + " "
                    + (person.lastName() == null ? "" : person.lastName().trim())).trim();
            notTdCustomer = true;
        } else if (login != null && !login.isBlank()) {
            name = login.trim();
            notTdCustomer = true;
        } else {
            name = "Klient Allegro";
            notTdCustomer = true;
        }

        String countryCode = address != null && address.countryCode() != null && !address.countryCode().isBlank()
                ? address.countryCode().trim()
                : "PL";

        return new BuyerBilling(
                name,
                notTdCustomer,
                countryCode,
                vatRegNo,
                address == null ? null : address.street(),
                address == null ? null : address.city(),
                address == null ? null : address.zipCode(),
                email,
                login);
    }

    default String resolveVatRegNo(AllegroInvoiceCompany company) {
        if (company == null) {
            return null;
        }
        if (company.ids() != null) {
            for (AllegroTaxId id : company.ids()) {
                if (id == null || id.value() == null || id.value().isBlank()) {
                    continue;
                }
                if (id.type() == null || "PL_NIP".equalsIgnoreCase(id.type()) || "OTHER".equalsIgnoreCase(id.type())) {
                    return id.value().trim();
                }
            }
            for (AllegroTaxId id : company.ids()) {
                if (id != null && id.value() != null && !id.value().isBlank()) {
                    return id.value().trim();
                }
            }
        }
        if (company.taxId() != null && !company.taxId().isBlank()) {
            return company.taxId().trim();
        }
        return null;
    }
}
