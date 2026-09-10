package pl.tw.ksiegowosc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import pl.tw.ksiegowosc.client.MeritApiClient;
import pl.tw.ksiegowosc.dto.MeritUnitDto;

class UnitsServiceTest {

    private MeritApiClient meritApiClient;
    private UnitsService unitsService;

    @BeforeEach
    void setUp() {
        meritApiClient = mock(MeritApiClient.class);
        unitsService = new UnitsService(meritApiClient);
    }

    @Test
    void shouldPreferSztUnitWhenAvailable() {
        List<MeritUnitDto> units = List.of(
                new MeritUnitDto("KG", "kg"),
                new MeritUnitDto("SZT", "szt."));

        MeritUnitDto unit = unitsService.requireDefaultUnit(units);

        assertThat(unit.name()).isEqualTo("szt.");
    }

    @Test
    void shouldFallBackToFirstUnitWhenPreferredMissing() {
        MeritUnitDto unit = unitsService.requireDefaultUnit(List.of(new MeritUnitDto("KG", "kg")));

        assertThat(unit.name()).isEqualTo("kg");
    }

    @Test
    void shouldFailWhenNoUnitsConfigured() {
        when(meritApiClient.getUnits()).thenReturn(List.of());

        assertThatThrownBy(() -> unitsService.requireDefaultUnit())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Jednostki miary");
    }
}
