package pl.tw.ksiegowosc.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import pl.tw.ksiegowosc.client.AllegroApiClient;
import pl.tw.ksiegowosc.dto.AllegroOfferDto;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroOffersResponse;
import pl.tw.ksiegowosc.entity.AllegroAccount;
import pl.tw.ksiegowosc.mapper.AllegroOfferMapper;

@Service
public class AllegroOffersService {

    private static final Logger log = LoggerFactory.getLogger(AllegroOffersService.class);

    private final AllegroApiClient allegroApiClient;
    private final AllegroAuthService authService;
    private final AllegroAccountService accountService;
    private final AllegroOfferMapper offerMapper;

    public AllegroOffersService(
            AllegroApiClient allegroApiClient,
            AllegroAuthService authService,
            AllegroAccountService accountService,
            AllegroOfferMapper offerMapper) {
        this.allegroApiClient = allegroApiClient;
        this.authService = authService;
        this.accountService = accountService;
        this.offerMapper = offerMapper;
    }

    public List<AllegroOfferDto> getOffers(int offset, int limit, String publicationStatus) {
        List<AllegroAccount> accounts = accountService.listConnectedAccounts();
        if (accounts.isEmpty()) {
            return List.of();
        }

        List<AllegroOfferDto> offers = new ArrayList<>();
        for (AllegroAccount account : accounts) {
            try {
                String token = authService.getValidAccessTokenForAccount(account);
                AllegroOffersResponse response =
                        allegroApiClient.getOffers(token, offset, limit, publicationStatus);
                if (response == null || response.offers() == null) {
                    continue;
                }
                for (AllegroOfferItem item : response.offers()) {
                    offers.add(offerMapper.toDto(item, account.getId(), account.getName()));
                }
            } catch (RuntimeException ex) {
                log.warn("Nie udało się pobrać ofert dla konta Allegro id={}", account.getId(), ex);
            }
        }
        return Collections.unmodifiableList(offers);
    }
}
