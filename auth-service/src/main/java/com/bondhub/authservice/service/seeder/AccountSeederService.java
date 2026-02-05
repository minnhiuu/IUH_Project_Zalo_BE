package com.bondhub.authservice.service.seeder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AccountSeederService {
    Map<String, Object> seedAccounts(int count);


}

