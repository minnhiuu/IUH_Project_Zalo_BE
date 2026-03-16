package com.bondhub.searchservice.mapper;

import com.bondhub.searchservice.dto.request.RecentSearchRequest;
import com.bondhub.searchservice.dto.response.RecentSearchResponse;
import com.bondhub.searchservice.model.redis.RecentSearch;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RecentSearchMapper {
    RecentSearch toModel(RecentSearchRequest request);
    RecentSearchResponse toResponse(RecentSearch model);
}
