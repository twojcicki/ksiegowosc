package pl.tw.ksiegowosc.mapper;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import pl.tw.ksiegowosc.dto.MeritTaxDto;
import pl.tw.ksiegowosc.service.TaxesService;

/**
 * Manual wiring of MapStruct Spring mappers for unit tests outside the Spring context.
 */
public final class MapperFixtures {

    private MapperFixtures() {
    }

    public static AllegroBillingMapper billingMapper() {
        return new AllegroBillingMapperImpl();
    }

    public static AllegroInvoiceMapper invoiceMapper() {
        AllegroInvoiceMapperImpl mapper = new AllegroInvoiceMapperImpl();
        AllegroToMeritInvoiceBuilder builder = new AllegroToMeritInvoiceBuilder(
                billingMapper(),
                new TaxesService(null) {
                    @Override
                    public List<MeritTaxDto> listTaxes() {
                        return sampleTaxes();
                    }
                });
        setField(mapper, AllegroInvoiceMapper.class, "invoiceBuilder", builder);
        return mapper;
    }

    public static List<MeritTaxDto> sampleTaxes() {
        return List.of(
                new MeritTaxDto("tax-23", "23", "VAT 23%", new BigDecimal("23")),
                new MeritTaxDto("tax-8", "8", "VAT 8%", new BigDecimal("8")));
    }

    public static AllegroSoldInvoiceMapper soldInvoiceMapper() {
        return new AllegroSoldInvoiceMapperImpl();
    }

    public static AllegroSoldItemMapper soldItemMapper() {
        return new AllegroSoldItemMapperImpl();
    }

    public static AllegroOfferMapper offerMapper() {
        return new AllegroOfferMapperImpl();
    }

    public static MeritInvoiceMapper meritInvoiceMapper() {
        return new MeritInvoiceMapperImpl();
    }

    public static AllegroTokenMapper tokenMapper() {
        return new AllegroTokenMapperImpl();
    }

    private static void setField(Object target, Class<?> declaringClass, String name, Object value) {
        try {
            Field field = declaringClass.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot set " + declaringClass.getSimpleName() + "." + name, ex);
        }
    }
}
