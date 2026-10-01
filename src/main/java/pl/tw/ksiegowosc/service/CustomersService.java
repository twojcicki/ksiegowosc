package pl.tw.ksiegowosc.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.CustomerDto;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerResponse;

@Service
public class CustomersService {

    private final MeritApiClient meritApiClient;

    public CustomersService(MeritApiClient meritApiClient) {
        this.meritApiClient = meritApiClient;
    }

    public List<CustomerDto> getCustomers(String name) {
        String filter = (name == null || name.isBlank()) ? null : name.trim();
        return meritApiClient.getCustomers(filter);
    }

    public MeritCreateCustomerResponse createCustomer(MeritCreateCustomerRequest request) {
        return meritApiClient.createCustomer(request);
    }

    /**
     * Szuka istniejącego klienta w Merit wyłącznie po nazwie ({@code Customer.Name}).
     * @return CustomerId albo null, gdy nie znaleziono
     */
    public String findCustomerId(MeritCreateCustomerRequest request) {
        if (request == null) {
            return null;
        }
        String name = blankToNull(request.name());
        if (name == null) {
            return null;
        }
        return pickCustomerIdByName(meritApiClient.getCustomers(name, null), name);
    }

    private static String pickCustomerIdByName(List<CustomerDto> customers, String name) {
        if (customers == null || customers.isEmpty()) {
            return null;
        }
        String normalizedName = normalize(name);
        for (CustomerDto customer : customers) {
            if (customer == null
                    || customer.customerId() == null
                    || customer.customerId().isBlank()) {
                continue;
            }
            if (normalizedName.equals(normalize(customer.name()))) {
                return customer.customerId().trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
