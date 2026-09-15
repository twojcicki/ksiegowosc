package pl.tw.ksiegowosc.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import pl.tw.ksiegowosc.dto.MeritCreateCustomerRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceCustomer;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceItem;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRequest;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceRow;
import pl.tw.ksiegowosc.dto.MeritCreateInvoiceTaxAmount;

class AllegroMeritInvoiceMappingsTest {

    @Test
    void rulesShouldCoverAllMeritWireFieldsIncludingNestedItem() {
        Set<String> expected = new LinkedHashSet<>();
        jsonProperties(MeritCreateCustomerRequest.class).forEach(expected::add);
        expected.add("Customer.Id");
        jsonProperties(MeritCreateInvoiceRequest.class).stream()
                .filter(name -> !"Customer".equals(name) && !"InvoiceRow".equals(name) && !"TaxAmount".equals(name))
                .forEach(expected::add);
        jsonProperties(MeritCreateInvoiceItem.class)
                .forEach(name -> expected.add("InvoiceRow[].Item." + name));
        jsonProperties(MeritCreateInvoiceRow.class).stream()
                .filter(name -> !"Item".equals(name))
                .forEach(name -> expected.add("InvoiceRow[]." + name));
        jsonProperties(MeritCreateInvoiceTaxAmount.class)
                .forEach(name -> expected.add("TaxAmount[]." + name));

        assertThat(jsonProperties(MeritCreateInvoiceCustomer.class)).containsExactly("Id");

        Set<String> actual = AllegroMeritInvoiceMappings.RULES.stream()
                .map(MeritFieldRule::meritField)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static Set<String> jsonProperties(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> {
                    String name = component.getName();
                    JsonProperty onComponent = component.getAnnotation(JsonProperty.class);
                    if (onComponent != null && !onComponent.value().isBlank()) {
                        return onComponent.value();
                    }
                    try {
                        Method accessor = type.getDeclaredMethod(name);
                        JsonProperty onMethod = accessor.getAnnotation(JsonProperty.class);
                        if (onMethod != null && !onMethod.value().isBlank()) {
                            return onMethod.value();
                        }
                    } catch (NoSuchMethodException ignored) {
                        // fall through
                    }
                    for (Parameter parameter : type.getDeclaredConstructors()[0].getParameters()) {
                        if (parameter.getName().equals(name)) {
                            JsonProperty onParam = parameter.getAnnotation(JsonProperty.class);
                            if (onParam != null && !onParam.value().isBlank()) {
                                return onParam.value();
                            }
                        }
                    }
                    throw new IllegalStateException("Missing @JsonProperty on " + type.getSimpleName() + "." + name);
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
