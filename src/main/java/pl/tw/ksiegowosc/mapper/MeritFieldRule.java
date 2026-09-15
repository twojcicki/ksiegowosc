package pl.tw.ksiegowosc.mapper;

public record MeritFieldRule(
        MeritFieldSection section,
        String meritField,
        String sourceRule
) {
}
