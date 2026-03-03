package com.bondhub.userservice.mapper;

import com.bondhub.userservice.dto.request.recentsearch.RecentSearchRequest;
import com.bondhub.userservice.dto.response.RecentSearchResponse;
import com.bondhub.userservice.model.redis.RecentSearch;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RecentSearchMapper {
    RecentSearch toModel(RecentSearchRequest request);

    RecentSearchResponse toResponse(RecentSearch model);
}