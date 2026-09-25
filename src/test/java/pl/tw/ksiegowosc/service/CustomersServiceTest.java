package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.CustomerDto;

class CustomersServiceTest {

    private MeritApiClient meritApiClient;
    private CustomersService customersService;

    @BeforeEach
    void setUp() {
        meritApiClient = mock(MeritApiClient.class);
        customersService = new CustomersService(meritApiClient);
    }

    @Test
    void shouldPassTrimmedNameToClient() {
        when(meritApiClient.getCustomers("Firma")).thenReturn(List.of(
                new CustomerDto("id", "Firma", null, null, null, null, null, null)));

        List<CustomerDto> customers = customersService.getCustomers("  Firma  ");

        assertThat(customers).hasSize(1);
        verify(meritApiClient).getCustomers("Firma");
    }

    @Test
    void shouldPassNullWhenNameIsBlank() {
        when(meritApiClient.getCustomers(null)).thenReturn(List.of());

        List<CustomerDto> customers = customersService.getCustomers("   ");

        assertThat(customers).isEmpty();
        verify(meritApiClient).getCustomers(null);
    }

    @Test
    void shouldPassVatRegNoToClient() {
        when(meritApiClient.getCustomers(null, "5252674798")).thenReturn(List.of(
                new CustomerDto("id", "Firma", null, "5252674798", null, null, null, null)));

        List<CustomerDto> customers = customersService.getCustomersByVatRegNo("  5252674798  ");

        assertThat(customers).hasSize(1);
        verify(meritApiClient).getCustomers(null, "5252674798");
    }

    @Test
    void shouldFindExactKlientAllegroUsingFixedSearchFilter() {
        when(meritApiClient.getCustomers("Klient Allegro")).thenReturn(List.of(
                new CustomerDto("c1", "Klient Allegro (other)", null, null, null, null, null, null),
                new CustomerDto("c2", "Klient Allegro (buyer1)", null, null, null, null, null, null)));

        Optional<CustomerDto> found = customersService.findCustomerByExactName("Klient Allegro (buyer1)");

        assertThat(found).isPresent();
        assertThat(found.get().customerId()).isEqualTo("c2");
        verify(meritApiClient).getCustomers("Klient Allegro");
    }

    @Test
    void shouldFindExactPersonName() {
        when(meritApiClient.getCustomers("Anna Nowak")).thenReturn(List.of(
                new CustomerDto("c1", "Anna Nowak", null, null, null, null, null, null)));

        Optional<CustomerDto> found = customersService.findCustomerByExactName("  Anna   Nowak ");

        assertThat(found).isPresent();
        assertThat(found.get().customerId()).isEqualTo("c1");
    }

    @Test
    void searchQueryForKlientAllegroUsesFixedText() {
        assertThat(CustomersService.searchQueryForName("Klient Allegro (x)")).isEqualTo("Klient Allegro");
        assertThat(CustomersService.searchQueryForName("Jan Kowalski")).isEqualTo("Jan Kowalski");
    }
}
