package com.bondhub.userservice.service.elasticsearch;


public interface UserSyncService {
    long reindexAll();

    void recreateIndex();

    long syncAllFromMongo();
}
