package pl.tw.ksiegowosc.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import pl.tw.ksiegowosc.dto.AllegroOfferDto;
import pl.tw.ksiegowosc.dto.allegro.AllegroOfferItem;
import pl.tw.ksiegowosc.dto.allegro.AllegroPrice;
import pl.tw.ksiegowosc.dto.allegro.AllegroSellingMode;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AllegroOfferMapper {

    @Mapping(target = "price", source = "sellingMode", qualifiedByName = "sellingModePrice")
    @Mapping(target = "currency", source = "sellingMode", qualifiedByName = "sellingModeCurrency")
    @Mapping(target = "available", source = "stock.available")
    @Mapping(target = "sold", source = "stock.sold")
    @Mapping(target = "publicationStatus", source = "publication.status")
    AllegroOfferDto toDto(AllegroOfferItem item);

    @Named("sellingModePrice")
    default BigDecimal sellingModePrice(AllegroSellingMode sellingMode) {
        AllegroPrice price = sellingMode == null ? null : sellingMode.price();
        if (price == null || price.amount() == null || price.amount().isBlank()) {
            return null;
        }
        return new BigDecimal(price.amount());
    }

    @Named("sellingModeCurrency")
    default String sellingModeCurrency(AllegroSellingMode sellingMode) {
        AllegroPrice price = sellingMode == null ? null : sellingMode.price();
        return price == null ? null : price.currency();
    }
}
