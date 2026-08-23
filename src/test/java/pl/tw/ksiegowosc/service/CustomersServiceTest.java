package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
    void shouldPassNullWhenNameIsMissing() {
        when(meritApiClient.getCustomers(null)).thenReturn(List.of());

        customersService.getCustomers(null);

        verify(meritApiClient).getCustomers(null);
    }
}
