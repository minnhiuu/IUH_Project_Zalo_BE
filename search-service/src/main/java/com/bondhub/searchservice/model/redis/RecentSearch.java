package com.bondhub.searchservice.model.redis;

import com.bondhub.searchservice.enums.SearchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecentSearch {
    private String id;
    private String name;
    private String avatar;
    private SearchType type;
    private long timestamp;
}
