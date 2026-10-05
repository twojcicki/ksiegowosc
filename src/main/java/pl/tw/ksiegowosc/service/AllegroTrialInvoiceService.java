package pl.tw.ksiegowosc.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pl.tw.ksiegowosc.dto.AllegroTrialInvoiceDto;
import pl.tw.ksiegowosc.entity.AllegroTrialInvoice;
import pl.tw.ksiegowosc.repository.AllegroTrialInvoiceRepository;

@Service
public class AllegroTrialInvoiceService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AllegroTrialInvoiceRepository trialInvoiceRepository;
    private final CurrentUserApiCredentialsService credentialsService;
    private final Clock clock;

    public AllegroTrialInvoiceService(
            AllegroTrialInvoiceRepository trialInvoiceRepository,
            CurrentUserApiCredentialsService credentialsService,
            Clock clock) {
        this.trialInvoiceRepository = trialInvoiceRepository;
        this.credentialsService = credentialsService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AllegroTrialInvoiceDto> listForCurrentUser() {
        Long userId = credentialsService.requireCurrentUserId();
        return trialInvoiceRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public AllegroTrialInvoiceDto requireForCurrentUser(Long id) {
        Long userId = credentialsService.requireCurrentUserId();
        AllegroTrialInvoice entity = trialInvoiceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono faktury próbnej."));
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public boolean existsByOrderId(String orderId) {
        return orderId != null && trialInvoiceRepository.existsByOrderId(orderId);
    }

    @Transactional
    public AllegroTrialInvoice save(
            Long userId,
            Long accountId,
            String orderId,
            String payloadJson) {
        if (orderId == null || orderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak orderId.");
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brak payloadu faktury próbnej.");
        }
        if (trialInvoiceRepository.existsByOrderId(orderId.trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Faktura próbna dla tego zamówienia już istnieje.");
        }
        AllegroTrialInvoice entity = new AllegroTrialInvoice();
        entity.setUserId(userId);
        entity.setAccountId(accountId);
        entity.setOrderId(orderId.trim());
        entity.setPayloadJson(payloadJson);
        entity.setCreatedAt(Instant.now(clock));
        return trialInvoiceRepository.save(entity);
    }

    private AllegroTrialInvoiceDto toDto(AllegroTrialInvoice entity) {
        return new AllegroTrialInvoiceDto(
                entity.getId(),
                entity.getAccountId(),
                entity.getOrderId(),
                entity.getPayloadJson(),
                entity.getCreatedAt(),
                extractInvoiceNo(entity.getPayloadJson()));
    }

    private static String extractInvoiceNo(String payloadJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(payloadJson);
            return Optional.ofNullable(root.get("InvoiceNo"))
                    .map(JsonNode::asText)
                    .filter(value -> value != null && !value.isBlank())
                    .orElse(null);
        } catch (JsonProcessingException | RuntimeException ex) {
            return null;
        }
    }
}
