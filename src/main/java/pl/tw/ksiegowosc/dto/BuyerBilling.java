package pl.tw.ksiegowosc.dto;

public record BuyerBilling(
        String name,
        boolean notTdCustomer,
        String countryCode,
        String vatRegNo,
        String address,
        String city,
        String postalCode,
        String email,
        String login) {
}
