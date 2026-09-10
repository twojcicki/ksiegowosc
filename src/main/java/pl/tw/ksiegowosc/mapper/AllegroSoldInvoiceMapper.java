package pl.tw.ksiegowosc.mapper;

import java.time.Instant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import pl.tw.ksiegowosc.entity.AllegroSoldInvoice;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AllegroSoldInvoiceMapper {

    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "invoiceNo", source = "invoiceNo")
    @Mapping(target = "meritInvoiceId", source = "meritInvoiceId")
    @Mapping(target = "createdAt", source = "createdAt")
    AllegroSoldInvoice toEntity(String orderId, String invoiceNo, String meritInvoiceId, Instant createdAt);
}
