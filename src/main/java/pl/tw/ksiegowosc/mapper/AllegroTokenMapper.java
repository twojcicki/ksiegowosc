package pl.tw.ksiegowosc.mapper;

import java.time.Clock;
import java.time.Instant;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import pl.tw.ksiegowosc.dto.AllegroTokenResponse;
import pl.tw.ksiegowosc.entity.AllegroToken;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AllegroTokenMapper {

    @Mapping(target = "id", constant = "default")
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "expiresAt", expression = "java(clock.instant().plusSeconds(response.expiresIn()))")
    @Mapping(target = "updatedAt", expression = "java(clock.instant())")
    AllegroToken toEntity(AllegroTokenResponse response, @Context Clock clock);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "expiresAt", expression = "java(clock.instant().plusSeconds(response.expiresIn()))")
    @Mapping(target = "updatedAt", expression = "java(clock.instant())")
    void updateEntity(AllegroTokenResponse response, @MappingTarget AllegroToken token, @Context Clock clock);

    default AllegroToken apply(AllegroTokenResponse response, AllegroToken existing, Clock clock) {
        if (existing == null) {
            return toEntity(response, clock);
        }
        updateEntity(response, existing, clock);
        return existing;
    }
}
