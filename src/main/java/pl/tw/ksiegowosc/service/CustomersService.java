package pl.tw.ksiegowosc.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.CustomerDto;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritCreateCustomerResponse;
import pl.tw.ksiegowosc.mapper.AllegroBillingMapper;

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

    /**
     * Looks up a customer by exact name. For names starting with {@code Klient Allegro},
     * Merit is queried with that fixed prefix (broad match), then filtered to exact Name.
     */
    public Optional<CustomerDto> findCustomerByExactName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String trimmed = name.trim();
        String query = searchQueryForName(trimmed);
        return getCustomers(query).stream()
                .filter(customer -> customer.customerId() != null && !customer.customerId().isBlank())
                .filter(customer -> namesEqual(trimmed, customer.name()))
                .findFirst();
    }

    static String searchQueryForName(String name) {
        String trimmed = name.trim().replaceAll("\\s+", " ");
        String normalized = normalizeName(trimmed);
        String prefix = normalizeName(AllegroBillingMapper.KLIENT_ALLEGRO);
        if (normalized.equals(prefix) || normalized.startsWith(prefix + " ") || normalized.startsWith(prefix + "(")) {
            return AllegroBillingMapper.KLIENT_ALLEGRO;
        }
        return trimmed;
    }

    static boolean namesEqual(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizeName(left).equals(normalizeName(right));
    }

    static String normalizeName(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public MeritCreateCustomerResponse createCustomer(MeritCreateCustomerRequest request) {
        return meritApiClient.createCustomer(request);
    }
}
