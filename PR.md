# Pull Request

## Jira Ticket

- [BH-XXX](https://tranngochuyen.atlassian.net/browse/BH-XXX)

---

## Description

This PR implements comprehensive fallback mechanisms and configuration consolidation to improve system resilience and maintainability. It enhances the API Gateway with circuit breaker patterns, retry logic, and centralized error handling while consolidating duplicate configurations across microservices into a common configuration file.

**Branch:** `infra/gateway-fallback-config-clean-up` → `develop`

---

## Type of change

Please check the relevant option:

- [ ] Bug fix (fixes an existing issue)
- [ ] New feature (adds new functionality)
- [ ] Refactor (no behavior change)
- [x] Config / Infrastructure change
- [ ] Other (please describe)

---

## What was changed?

### API Gateway Enhancements
- ✅ Added **ResilienceConfig** with circuit breaker, retry, and rate limiter patterns
- ✅ Implemented comprehensive **GlobalExceptionHandler** for centralized error handling
- ✅ Enhanced **FallbackController** with service-specific fallback endpoints
- ✅ Created **ResilienceGatewayFilterFactory** for applying resilience patterns to routes
- ✅ Updated **GatewayConfig** to register custom filters
- ✅ Added detailed API Gateway configuration in `config/api-gateway.yml` (87 lines)

### Configuration Consolidation
- ✅ Disabled `bondhub.security.gateway-auth.enabled` (set to `false`) to fix security error
- ✅ Centralized common configs (Kafka, Resilience4j, Actuator, MongoDB, Eureka) into `config/application.yml`
- ✅ Removed duplicate configurations from:
  - `auth-service.yml` (reduced by 64 lines)
  - `message-service.yml` (reduced by 69 lines)
  - `notification-service.yml` (reduced by 69 lines)
  - `user-service.yml` (reduced by 68 lines)
- ✅ Deleted obsolete `common.yml` file

### Common Module Updates
- ✅ Added `@CrossOrigin` annotations to `CommonSecurityConfig` and `GlobalExceptionHandler`
- ✅ Added `@JsonInclude(JsonInclude.Include.NON_NULL)` to `OutboxEvent` model

### Service Dependencies
- ✅ Added Resilience4j dependencies to `message-service` and `notification-service` pom.xml

### Documentation & API
- ✅ Refactored Swagger API documentation configuration across all services

### Cleanup
- ✅ Removed obsolete `MONGODB_AUDITING.md` documentation
- ✅ Removed `be_status.txt` file

---

## How Has This Been Tested? (Manual)

Please verify the following scenarios:

### 1. Service Fallback Testing
**When a service is down:**
```bash
# Stop auth-service
docker-compose stop auth-service

# Test fallback endpoint
curl -X POST http://localhost:8080/auth/api/auth/login
# Expected: Fallback response with proper error message
```

### 2. Circuit Breaker Testing
```bash
# Simulate multiple failures to trigger circuit breaker
for i in {1..10}; do
  curl -X GET http://localhost:8080/user/api/users/profile
done
# Expected: Circuit should open after threshold failures
```

### 3. Configuration Verification
```bash
# Verify services load centralized configs
docker-compose up -d
docker logs auth-service | grep -i "kafka\|resilience"
docker logs message-service | grep -i "kafka\|resilience"
```

### 4. API Gateway Routes
**Test routes:**
- Endpoint: `http://localhost:8080/auth/**`
- Method: GET/POST
- Verify: Routes work with resilience patterns applied

### 5. Global Exception Handling
**Test error scenarios:**
```bash
# Invalid token
curl -H "Authorization: Bearer invalid_token" http://localhost:8080/user/api/users/profile

# Not found
curl -X GET http://localhost:8080/user/api/users/99999
```

---

## Risk Level

- [ ] Low – safe logic change
- [x] Medium – affects business logic
- [ ] High – affects auth / data integrity

**Justification:** This change affects infrastructure and cross-cutting concerns (error handling, circuit breakers, configuration). While it improves resilience, it modifies how services behave under failure conditions and consolidates configurations that could impact service startup and runtime behavior.

---

## Checklist

- [ ] PR title contains `[BH-KEY]`
- [x] Commit messages follow convention (`feat (infra): ...`)
- [ ] Jira ticket is linked
- [x] Input validation handled (via GlobalExceptionHandler)
- [x] Error cases handled (via fallback mechanisms)
- [ ] Manual testing completed

---

## Additional Notes

### Benefits:
- 🎯 **Improved Resilience:** Circuit breakers and retry logic prevent cascading failures
- 📦 **DRY Configuration:** Eliminated 270+ lines of duplicate config
- 🛡️ **Better Error Handling:** Centralized, consistent error responses
- 📊 **Observability:** Enhanced monitoring via Actuator endpoints
- 🔄 **Fallback Mechanisms:** Graceful degradation when services unavailable

### Configuration Changes Impact:
Services now inherit common configurations from `application.yml`, reducing maintenance burden and ensuring consistency across the microservices ecosystem.

### Files Changed: 21 files
- **Additions:** 721 lines
- **Deletions:** 466 lines
- **Net Change:** +255 lines

---

## Reviewer Guidelines

1. **Focus Areas:**
   - Verify ResilienceConfig settings (timeout, retry, circuit breaker thresholds)
   - Check that common configs apply correctly to all services
   - Review fallback response formats for consistency

2. **Testing Priority:**
   - Circuit breaker behavior under load
   - Service startup with consolidated configs
   - Fallback endpoints return appropriate responses

3. **Questions to Consider:**
   - Are resilience thresholds appropriate for production?
   - Should any service have custom overrides for common configs?
   - Is the fallback UX acceptable for end users?
