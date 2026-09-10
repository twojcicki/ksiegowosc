package pl.tw.ksiegowosc.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.MeritUnitDto;

@Service
public class UnitsService {

    private static final List<String> PREFERRED_NAMES = List.of("szt", "szt.", "sztuka", "sztuki");

    private final MeritApiClient meritApiClient;

    public UnitsService(MeritApiClient meritApiClient) {
        this.meritApiClient = meritApiClient;
    }

    public List<MeritUnitDto> listUnits() {
        return meritApiClient.getUnits();
    }

    public MeritUnitDto requireDefaultUnit() {
        return requireDefaultUnit(listUnits());
    }

    public MeritUnitDto requireDefaultUnit(List<MeritUnitDto> units) {
        List<MeritUnitDto> source = units == null ? List.of() : units;
        if (source.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Brak jednostek miary w Merit (Ustawienia → Jednostki miary).");
        }
        return source.stream()
                .filter(this::isPreferred)
                .findFirst()
                .orElse(source.getFirst());
    }

    private boolean isPreferred(MeritUnitDto unit) {
        if (unit == null) {
            return false;
        }
        return matchesPreferred(unit.name()) || matchesPreferred(unit.code());
    }

    private static boolean matchesPreferred(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return PREFERRED_NAMES.contains(normalized);
    }
}
