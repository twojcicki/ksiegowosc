package pl.tw.ksiegowosc.mapper;

public final class MappingSupport {

    private MappingSupport() {
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
