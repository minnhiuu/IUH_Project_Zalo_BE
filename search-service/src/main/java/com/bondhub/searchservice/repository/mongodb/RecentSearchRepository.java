package com.bondhub.searchservice.repository.mongodb;

import com.bondhub.searchservice.enums.SearchType;
import com.bondhub.searchservice.model.mongodb.RecentSearch;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecentSearchRepository extends MongoRepository<RecentSearch, String> {
    
    List<RecentSearch> findAllByUserIdAndTypeInOrderByTimestampDesc(String userId, Collection<SearchType> types);
    
    List<RecentSearch> findAllByUserIdAndTypeOrderByTimestampDesc(String userId, SearchType type);
    
    Optional<RecentSearch> findByUserIdAndTargetIdAndType(String userId, String targetId, SearchType type);
    
    Optional<RecentSearch> findByUserIdAndNameIgnoreCaseAndType(String userId, String name, SearchType type);
    
    void deleteAllByUserId(String userId);
    
    void deleteByUserIdAndTargetIdAndType(String userId, String targetId, SearchType type);
    
    long countByUserIdAndType(String userId, SearchType type);
    
    List<RecentSearch> findAllByUserIdAndTypeOrderByTimestampAsc(String userId, SearchType type);
}
