package pl.tw.ksiegowosc.service;

import java.util.List;

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

    public List<CustomerDto> getCustomersByVatRegNo(String vatRegNo) {
        String filter = (vatRegNo == null || vatRegNo.isBlank()) ? null : vatRegNo.trim();
        if (filter == null) {
            return List.of();
        }
        return meritApiClient.getCustomers(null, filter);
    }

    public MeritCreateCustomerResponse createCustomer(MeritCreateCustomerRequest request) {
        return meritApiClient.createCustomer(request);
    }
}
