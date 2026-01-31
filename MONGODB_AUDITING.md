# MongoDB Auditing Configuration

MongoDB auditing has been enabled for all services using Spring Data MongoDB. This automatically tracks when records are created and modified, along with who made the changes.

## What Was Implemented

### 1. Audit Configuration (`common/src/main/java/com/bondhub/common/config/MongoAuditConfig.java`)

- Created `@EnableMongoAuditing` configuration
- Implemented `AuditorAware<String>` bean that retrieves the current user from Spring Security context
- Falls back to "system" if no authenticated user is found

### 2. Base Model (`common/src/main/java/com/bondhub/common/model/BaseModel.java`)

Already had the necessary audit fields:

- `@CreatedDate` - Automatically set when entity is created
- `@LastModifiedDate` - Automatically updated when entity is modified
- `@CreatedBy` - Set to current user when entity is created
- `@LastModifiedBy` - Set to current user when entity is modified

### 3. Service Applications

Added `@EnableMongoAuditing` to:

- `UserServiceApplication`
- `MessageServiceApplication`
- `NotificationServicesApplication`
- `AuthServiceApplication`

## How It Works

1. **Automatic Timestamping**: When you save an entity that extends `BaseModel`:
   - On **create**: `createdAt` and `createdBy` are automatically populated
   - On **update**: `lastModifiedAt` and `lastModifiedBy` are automatically updated

2. **User Tracking**: The `AuditorAware` implementation extracts the authenticated user from the Spring Security context:

   ```java
   Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
   ```

   - If authenticated: Uses the authenticated username
   - If not authenticated or anonymous: Uses "system"

3. **Entity Setup**: Any entity extending `BaseModel` automatically gets audit tracking:
   ```java
   @Document("users")
   public class User extends BaseModel {
       // Your fields here
   }
   ```

## Testing Auditing

To verify auditing is working:

1. **Create a new entity**:

   ```java
   User user = new User();
   user.setFullName("John Doe");
   userRepository.save(user);
   ```

   After save, check:
   - `user.getCreatedAt()` - should be current timestamp
   - `user.getCreatedBy()` - should be the authenticated username or "system"

2. **Update an existing entity**:

   ```java
   user.setFullName("Jane Doe");
   userRepository.save(user);
   ```

   After save, check:
   - `user.getLastModifiedAt()` - should be updated to current timestamp
   - `user.getLastModifiedBy()` - should be the authenticated username or "system"

3. **Check in MongoDB**:
   ```bash
   db.users.findOne()
   ```
   Should show:
   ```json
   {
     "_id": "...",
     "fullName": "John Doe",
     "createdAt": ISODate("2026-01-28T..."),
     "lastModifiedAt": ISODate("2026-01-28T..."),
     "createdBy": "john.doe@example.com",
     "lastModifiedBy": "john.doe@example.com"
   }
   ```

## Services with Auditing Enabled

- ✅ **auth-service** - Tracks Account creation/modifications
- ✅ **user-service** - Tracks User creation/modifications
- ✅ **message-service** - Tracks Message creation/modifications
- ✅ **notification-service** - Tracks Notification creation/modifications

## Troubleshooting

### Audit fields are null

- Ensure the entity extends `BaseModel`
- Verify `@EnableMongoAuditing` is present on the application class
- Check that the `MongoAuditConfig` is being scanned (it's in common module which is component-scanned)

### "system" instead of username

- Verify Spring Security is configured correctly
- Check that the user is authenticated when saving
- Ensure the SecurityContext contains the authentication

### Timestamps not updating

- Make sure you're calling `repository.save()` to trigger the audit
- Verify the entity extends `BaseModel` properly
- Check that auto-index-creation is enabled in MongoDB config
