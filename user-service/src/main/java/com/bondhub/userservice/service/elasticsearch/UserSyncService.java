package com.bondhub.userservice.service.elasticsearch;


public interface UserSyncService {
    long reindexAll();
    
    void switchAliasToLatestIndex();
}
