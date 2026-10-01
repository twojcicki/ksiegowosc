package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.CustomerDto;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;

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
    void findCustomerId_shouldLookupOnlyByName() {
        MeritCreateCustomerRequest request = new MeritCreateCustomerRequest(
                "  ACME Sp. z o.o.  ", true, "PL", "5252674798",
                null, null, null, null, "PLN", "PL");
        when(meritApiClient.getCustomers("ACME Sp. z o.o.", null)).thenReturn(List.of(
                new CustomerDto("cust-1", "Other", null, "5252674798", null, null, null, null),
                new CustomerDto("cust-2", "acme sp. z o.o.", null, "1111111111", null, null, null, null)));

        String id = customersService.findCustomerId(request);

        assertThat(id).isEqualTo("cust-2");
        verify(meritApiClient).getCustomers("ACME Sp. z o.o.", null);
        verify(meritApiClient, never()).getCustomers(null, "5252674798");
    }

    @Test
    void findCustomerId_shouldReturnNullWhenNameMissing() {
        MeritCreateCustomerRequest request = new MeritCreateCustomerRequest(
                "  ", true, "PL", "5252674798",
                null, null, null, null, "PLN", "PL");

        assertThat(customersService.findCustomerId(request)).isNull();
        verify(meritApiClient, never()).getCustomers(any(), any());
    }

    @Test
    void findCustomerId_shouldReturnNullWhenNoExactNameMatch() {
        MeritCreateCustomerRequest request = new MeritCreateCustomerRequest(
                "ACME", true, "PL", null,
                null, null, null, null, "PLN", "PL");
        when(meritApiClient.getCustomers("ACME", null)).thenReturn(List.of(
                new CustomerDto("cust-1", "ACME Poland", null, null, null, null, null, null)));

        assertThat(customersService.findCustomerId(request)).isNull();
    }
}
