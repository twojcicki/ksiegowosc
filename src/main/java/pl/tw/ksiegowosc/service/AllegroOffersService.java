package pl.tw.ksiegowosc.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.dto.AllegroOfferDto;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroOffersResponse;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.dto.allegro.AllegroPublication;
import pl.tw.ksiegowosc.dto.allegro.AllegroSellingMode;
import pl.tw.ksiegowosc.dto.allegro.AllegroStock;

@Service
public class AllegroOffersService {

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;

    public AllegroOffersService(AllegroApiClient allegroApiClient, AllegroAuthService authService) {
        this.allegroApiClient = allegroApiClient;
        this.authService = authService;
    }

    public List<AllegroOfferDto> getOffers(int offset, int limit, String publicationStatus) {
        authService.getValidAccessToken();
        AllegroOffersResponse response = allegroApiClient.getOffers(offset, limit, publicationStatus);
        if (response == null || response.offers() == null) {
            return List.of();
        }
        List<AllegroOfferDto> offers = new ArrayList<>(response.offers().size());
        for (AllegroOfferItem item : response.offers()) {
            offers.add(toDto(item));
        }
        return Collections.unmodifiableList(offers);
    }

    private static AllegroOfferDto toDto(AllegroOfferItem item) {
        AllegroSellingMode sellingMode = item.sellingMode();
        AllegroPrice price = sellingMode == null ? null : sellingMode.price();
        AllegroStock stock = item.stock();
        AllegroPublication publication = item.publication();

        return new AllegroOfferDto(
                item.id(),
                item.name(),
                parseAmount(price == null ? null : price.amount()),
                price == null ? null : price.currency(),
                stock == null ? null : stock.available(),
                stock == null ? null : stock.sold(),
                publication == null ? null : publication.status());
    }

    private static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        return new BigDecimal(amount);
    }
}
