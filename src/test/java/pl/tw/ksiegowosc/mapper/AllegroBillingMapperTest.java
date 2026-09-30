package pl.tw.ksiegowosc.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.dto.BuyerBilling;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyer;
import pl.tw.ksiegowosc.dto.allegro.AllegroBuyerAddress;
import pl.tw.ksiegowosc.dto.allegro.AllegroCheckoutForm;
import pl.tw.ksiegowosc.dto.allegro.AllegroDelivery;
import pl.tw.ksiegowosc.dto.allegro.AllegroDeliveryAddress;
import pl.tw.ksiegowosc.dto.allegro.AllegroDeliveryMethod;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoice;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoiceAddress;
import pl.tw.ksiegowosc.dto.allegro.AllegroInvoiceCompany;
import pl.tw.ksiegowosc.dto.allegro.AllegroNaturalPerson;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.dto.allegro.AllegroTaxId;

class AllegroBillingMapperTest {

    private final AllegroBillingMapper mapper = MapperFixtures.billingMapper();

    @Test
    void shouldMapNaturalPersonNameAndInvoiceAddressWhenRequired() {
        BuyerBilling billing = mapper.toBuyerBilling(invoiceRequiredForm(
                new AllegroInvoiceAddress(
                        "Ul. Faktury 1",
                        "Kraków",
                        "30-001",
                        "PL",
                        null,
                        new AllegroNaturalPerson("Anna", "Nowak")),
                null));

        assertThat(billing.name()).isEqualTo("Anna Nowak");
        assertThat(billing.notTdCustomer()).isTrue();
        assertThat(billing.vatRegNo()).isNull();
        assertThat(billing.address()).isEqualTo("Ul. Faktury 1");
        assertThat(billing.city()).isEqualTo("Kraków");
        assertThat(billing.postalCode()).isEqualTo("30-001");
    }

    @Test
    void shouldUseDeliveryAddressWhenInvoiceAddressMissingAndRequired() {
        AllegroDelivery delivery = new AllegroDelivery(
                new AllegroPrice("10.00", "PLN"),
                new AllegroDeliveryMethod("m1", "Kurier"),
                new AllegroDeliveryAddress("Jan", "Kowalski", "Dostawcza 2", "Gdańsk", "80-001", "PL", null));

        BuyerBilling billing = mapper.toBuyerBilling(invoiceRequiredForm(null, delivery));

        assertThat(billing.name()).isEqualTo("Klient Allegro (buyer1)");
        assertThat(billing.address()).isEqualTo("Dostawcza 2");
        assertThat(billing.city()).isEqualTo("Gdańsk");
        assertThat(billing.postalCode()).isEqualTo("80-001");
        assertThat(billing.countryCode()).isEqualTo("PL");
    }

    @Test
    void shouldNotMergeDeliveryIntoPartialInvoiceAddressWhenRequired() {
        AllegroDelivery delivery = new AllegroDelivery(
                null,
                null,
                new AllegroDeliveryAddress(null, null, "Dostawcza 2", "Gdańsk", "80-001", "PL", null));

        BuyerBilling billing = mapper.toBuyerBilling(invoiceRequiredForm(
                new AllegroInvoiceAddress("Tylko ulica", null, null, "PL", null, new AllegroNaturalPerson("Ewa", "Lis")),
                delivery));

        assertThat(billing.address()).isEqualTo("Tylko ulica");
        assertThat(billing.city()).isNull();
        assertThat(billing.postalCode()).isNull();
    }

    @Test
    void shouldMapDeliveryNameAndAddressWhenInvoiceNotRequired() {
        AllegroCheckoutForm form = new AllegroCheckoutForm(
                "order-1",
                new AllegroBuyer(
                        "buyer1",
                        "buyer@example.com",
                        "Jan",
                        "Kowalski",
                        new AllegroBuyerAddress("Kupiecka 3", "Warszawa", "00-001", "PL")),
                "READY_FOR_PROCESSING",
                null,
                new AllegroInvoice(false, new AllegroInvoiceAddress(
                        "Fakturowa 1",
                        "Kraków",
                        "30-001",
                        "PL",
                        new AllegroInvoiceCompany("Firma", "5252674798", null),
                        null)),
                List.of(),
                new AllegroDelivery(
                        null,
                        null,
                        new AllegroDeliveryAddress("Zbigniew", "Glinicki", "Dostawcza 2", "Gdańsk", "80-001", "PL", "Firma Dostawy")),
                null,
                null,
                null);

        BuyerBilling billing = mapper.toBuyerBilling(form);

        assertThat(billing.name()).isEqualTo("Zbigniew Glinicki");
        assertThat(billing.notTdCustomer()).isTrue();
        assertThat(billing.vatRegNo()).isNull();
        assertThat(billing.address()).isEqualTo("Dostawcza 2");
        assertThat(billing.city()).isEqualTo("Gdańsk");
        assertThat(billing.postalCode()).isEqualTo("80-001");
        assertThat(billing.countryCode()).isEqualTo("PL");
        assertThat(billing.email()).isEqualTo("buyer@example.com");
    }

    @Test
    void shouldBuildKlientAllegroFromEmailWhenDeliveryNameMissingAndNotRequired() {
        AllegroCheckoutForm form = new AllegroCheckoutForm(
                "order-1",
                new AllegroBuyer(null, "guest@example.com", null, null, null),
                "READY_FOR_PROCESSING",
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null);

        assertThat(mapper.toBuyerBilling(form).name()).isEqualTo("Klient Allegro (guest@example.com)");
    }

    @Test
    void shouldKeepCompanyWithNipAsTdCustomerWhenRequired() {
        BuyerBilling billing = mapper.toBuyerBilling(invoiceRequiredForm(
                new AllegroInvoiceAddress(
                        "Grunwaldzka 1",
                        "Poznań",
                        "60-166",
                        "PL",
                        new AllegroInvoiceCompany(
                                "Firma Sp. z o.o.",
                                "5252674798",
                                List.of(new AllegroTaxId("PL_NIP", "5252674798"))),
                        null),
                null));

        assertThat(billing.name()).isEqualTo("Firma Sp. z o.o.");
        assertThat(billing.notTdCustomer()).isFalse();
        assertThat(billing.vatRegNo()).isEqualTo("5252674798");
    }

    private static AllegroCheckoutForm invoiceRequiredForm(
            AllegroInvoiceAddress invoiceAddress, AllegroDelivery delivery) {
        return new AllegroCheckoutForm(
                "order-1",
                new AllegroBuyer("buyer1", "buyer@example.com", null, null, null),
                "READY_FOR_PROCESSING",
                null,
                new AllegroInvoice(true, invoiceAddress),
                List.of(),
                delivery,
                null,
                null,
                null);
    }
}
