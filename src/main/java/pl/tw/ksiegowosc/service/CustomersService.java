package pl.tw.ksiegowosc.service;

import java.util.List;

import org.springframework.stereotype.Service;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.CustomerDto;

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
}
