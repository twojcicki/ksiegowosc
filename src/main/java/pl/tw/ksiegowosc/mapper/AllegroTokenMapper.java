package pl.tw.ksiegowosc.mapper;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import pl.tw.ksiegowosc.dto.AllegroTokenResponse;
import pl.tw.ksiegowosc.entity.AllegroToken;

import java.time.Clock;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AllegroTokenMapper {

    @Mapping(target = "userId", expression = "java(userId)")
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "expiresAt", expression = "java(clock.instant().plusSeconds(response.expiresIn()))")
    @Mapping(target = "updatedAt", expression = "java(clock.instant())")
    AllegroToken toEntity(AllegroTokenResponse response, @Context Long userId, @Context Clock clock);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    @Mapping(target = "expiresAt", expression = "java(clock.instant().plusSeconds(response.expiresIn()))")
    @Mapping(target = "updatedAt", expression = "java(clock.instant())")
    void updateEntity(AllegroTokenResponse response, @MappingTarget AllegroToken token, @Context Clock clock);

    default AllegroToken apply(AllegroTokenResponse response, AllegroToken existing, Long userId, Clock clock) {
        if (existing == null) {
            return toEntity(response, userId, clock);
        }
        updateEntity(response, existing, clock);
        return existing;
    }
}
