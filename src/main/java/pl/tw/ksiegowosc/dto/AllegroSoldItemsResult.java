package pl.tw.ksiegowosc.dto;

import java.util.List;

public record AllegroSoldItemsResult(
        List<AllegroSoldItemDto> items,
        List<String> warnings
) {
    public AllegroSoldItemsResult {
        items = items == null ? List.of() : List.copyOf(items);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
