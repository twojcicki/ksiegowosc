package pl.tw.ksiegowosc.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import pl.tw.ksiegowosc.dto.BuyerBilling;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyerAddress;
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

    String KLIENT_ALLEGRO = "Klient Allegro";

    default BuyerBilling toBuyerBilling(AllegroCheckoutForm form) {
        AllegroBuyer buyer = form.buyer();
        String login = buyer == null ? null : buyer.login();
        String email = buyer == null ? null : buyer.email();

        AllegroInvoice invoice = form.invoice();
        if (!Boolean.TRUE.equals(invoice == null ? null : invoice.required())) {
            return buyerBillingWithoutInvoice(buyer, login, email);
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
            if (!hasText(name)) {
                name = klientAllegroName(login, email);
            }
            notTdCustomer = true;
        }

        ResolvedAddress address = resolveInvoiceOrDeliveryAddress(form);
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

    default BuyerBilling buyerBillingWithoutInvoice(AllegroBuyer buyer, String login, String email) {
        String name = buyerPersonFullName(buyer);
        if (!hasText(name)) {
            name = klientAllegroName(login, email);
        }
        ResolvedAddress address = resolveBuyerAddress(buyer == null ? null : buyer.address());
        return new BuyerBilling(
                name,
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

    static String buyerPersonFullName(AllegroBuyer buyer) {
        if (buyer == null) {
            return null;
        }
        String full = ((buyer.firstName() == null ? "" : buyer.firstName().trim()) + " "
                + (buyer.lastName() == null ? "" : buyer.lastName().trim())).trim();
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

    static String klientAllegroName(String login, String email) {
        String hint = hasText(login) ? login.trim() : (hasText(email) ? email.trim() : null);
        if (hint == null) {
            return KLIENT_ALLEGRO;
        }
        return KLIENT_ALLEGRO + " (" + hint + ")";
    }

    /**
     * Whole invoice.address XOR whole delivery.address — never merge fields from both.
     * Used only when invoice.required is true.
     */
    default ResolvedAddress resolveInvoiceOrDeliveryAddress(AllegroCheckoutForm form) {
        AllegroInvoice invoice = form.invoice();
        AllegroInvoiceAddress invoiceAddress = invoice == null ? null : invoice.address();
        if (isUsableInvoiceAddress(invoiceAddress)) {
            String country = hasText(invoiceAddress.countryCode()) ? invoiceAddress.countryCode().trim() : "PL";
            return new ResolvedAddress(
                    blankToNull(invoiceAddress.street()),
                    blankToNull(invoiceAddress.city()),
                    blankToNull(invoiceAddress.zipCode()),
                    country);
        }
        AllegroDelivery delivery = form.delivery();
        AllegroDeliveryAddress deliveryAddress = delivery == null ? null : delivery.address();
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

    static ResolvedAddress resolveBuyerAddress(AllegroBuyerAddress address) {
        if (address == null) {
            return new ResolvedAddress(null, null, null, "PL");
        }
        boolean usable = hasText(address.street()) || hasText(address.city()) || hasText(address.postCode());
        if (!usable) {
            return new ResolvedAddress(null, null, null, "PL");
        }
        String country = hasText(address.countryCode()) ? address.countryCode().trim() : "PL";
        return new ResolvedAddress(
                blankToNull(address.street()),
                blankToNull(address.city()),
                blankToNull(address.postCode()),
                country);
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
