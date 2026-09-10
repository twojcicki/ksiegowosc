package pl.tw.ksiegowosc.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.dto.AllegroOfferDto;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroOffersResponse;
import pl.tw.ksiegowosc.mapper.AllegroOfferMapper;

@Service
public class AllegroOffersService {

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;
    private final AllegroOfferMapper offerMapper;

    public AllegroOffersService(
            AllegroApiClient allegroApiClient,
            AllegroAuthService authService,
            AllegroOfferMapper offerMapper) {
        this.allegroApiClient = allegroApiClient;
        this.authService = authService;
        this.offerMapper = offerMapper;
    }

    public List<AllegroOfferDto> getOffers(int offset, int limit, String publicationStatus) {
        authService.getValidAccessToken();
        AllegroOffersResponse response = allegroApiClient.getOffers(offset, limit, publicationStatus);
        if (response == null || response.offers() == null) {
            return List.of();
        }
        List<AllegroOfferDto> offers = new ArrayList<>(response.offers().size());
        for (AllegroOfferItem item : response.offers()) {
            offers.add(offerMapper.toDto(item));
        }
        return Collections.unmodifiableList(offers);
    }
}
