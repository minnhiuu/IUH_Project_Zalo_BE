package com.bondhub.searchservice.mapper;

import com.bondhub.searchservice.dto.request.RecentSearchRequest;
import com.bondhub.searchservice.dto.response.RecentSearchResponse;
import com.bondhub.searchservice.model.mongodb.RecentSearch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RecentSearchMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "targetId", source = "id")
    RecentSearch toModel(RecentSearchRequest request);

    @Mapping(target = "id", source = "targetId")
    RecentSearchResponse toResponse(RecentSearch model);
}
