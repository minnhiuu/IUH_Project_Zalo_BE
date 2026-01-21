package com.bondhub.authservice.repository;

import com.bondhub.authservice.model.BlacklistedAccessToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlacklistedAccessTokenRepository extends CrudRepository<BlacklistedAccessToken, String> {

    boolean existsByJti(String jti);
}
