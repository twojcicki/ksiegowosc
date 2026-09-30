package pl.tw.ksiegowosc.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import pl.tw.ksiegowosc.dto.BuyerBilling;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroDelivery;
import pl.tw.ksiegowosc.dto.allegro.AllegroDeliveryAddress;
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
        if (!Boolean.TRUE.equals(invoice == null ? null : invoice.required())) {
            return billingFromDelivery(form.delivery(), login, email);
        }

        AllegroInvoiceAddress invoiceAddress = invoice.address();
        AllegroInvoiceCompany company = invoiceAddress == null ? null : invoiceAddress.company();
        AllegroNaturalPerson person = invoiceAddress == null ? null : invoiceAddress.naturalPerson();

        String vatRegNo = resolveVatRegNo(company);
        String name;
        boolean notTdCustomer;
        if (company != null && hasText(company.name())) {
            name = company.name().trim();
            notTdCustomer = !hasText(vatRegNo);
        } else {
            name = personFullName(person);
            notTdCustomer = true;
        }

        ResolvedAddress address = resolveInvoiceAddressOnly(invoiceAddress);
        return new BuyerBilling(
                name,
                notTdCustomer,
                address.countryCode(),
                vatRegNo,
                address.street(),
                address.city(),
                address.postalCode(),
                email,
                login);
    }

    default BuyerBilling billingFromDelivery(AllegroDelivery delivery, String login, String email) {
        AllegroDeliveryAddress deliveryAddress = delivery == null ? null : delivery.address();
        ResolvedAddress address = resolveDeliveryAddressOnly(deliveryAddress);
        return new BuyerBilling(
                deliveryPartyName(deliveryAddress),
                true,
                address.countryCode(),
                null,
                address.street(),
                address.city(),
                address.postalCode(),
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

    static String deliveryPartyName(AllegroDeliveryAddress address) {
        if (address == null) {
            return null;
        }
        String full = ((address.firstName() == null ? "" : address.firstName().trim()) + " "
                + (address.lastName() == null ? "" : address.lastName().trim())).trim();
        return full.isEmpty() ? null : full;
    }

    static String personFullName(AllegroNaturalPerson person) {
        if (person == null) {
            return null;
        }
        String full = ((person.firstName() == null ? "" : person.firstName().trim()) + " "
                + (person.lastName() == null ? "" : person.lastName().trim())).trim();
        return full.isEmpty() ? null : full;
    }

    /** Whole invoice.address only — used when invoice.required is true. */
    static ResolvedAddress resolveInvoiceAddressOnly(AllegroInvoiceAddress invoiceAddress) {
        if (isUsableInvoiceAddress(invoiceAddress)) {
            String country = hasText(invoiceAddress.countryCode()) ? invoiceAddress.countryCode().trim() : "PL";
            return new ResolvedAddress(
                    blankToNull(invoiceAddress.street()),
                    blankToNull(invoiceAddress.city()),
                    blankToNull(invoiceAddress.zipCode()),
                    country);
        }
        return new ResolvedAddress(null, null, null, "PL");
    }

    static ResolvedAddress resolveDeliveryAddressOnly(AllegroDeliveryAddress deliveryAddress) {
        if (isUsableDeliveryAddress(deliveryAddress)) {
            String country = hasText(deliveryAddress.countryCode()) ? deliveryAddress.countryCode().trim() : "PL";
            return new ResolvedAddress(
                    blankToNull(deliveryAddress.street()),
                    blankToNull(deliveryAddress.city()),
                    blankToNull(deliveryAddress.zipCode()),
                    country);
        }
        return new ResolvedAddress(null, null, null, "PL");
    }

    static boolean isUsableInvoiceAddress(AllegroInvoiceAddress address) {
        return address != null
                && (hasText(address.street()) || hasText(address.city()) || hasText(address.zipCode()));
    }

    static boolean isUsableDeliveryAddress(AllegroDeliveryAddress address) {
        return address != null
                && (hasText(address.street()) || hasText(address.city()) || hasText(address.zipCode()));
    }

    static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record ResolvedAddress(String street, String city, String postalCode, String countryCode) {
    }
}
