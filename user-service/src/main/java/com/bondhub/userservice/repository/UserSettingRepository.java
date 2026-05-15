package com.bondhub.userservice.repository;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.bondhub.common.dto.client.userservice.user.response.UserSearchVisibilityResponse;
import com.bondhub.common.enums.SearchVisibility;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.userservice.model.UserSetting;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Custom repository for UserSetting operations using MongoTemplate with dot
 * notation.
 * This approach prevents loading the entire User document into memory when
 * updating settings.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class UserSettingRepository {

    private final MongoTemplate mongoTemplate;
    private static final String USER_COLLECTION = "users";
    private static final String USER_SETTING_FIELD = "userSetting";

    /**
     * Get UserSetting by userId using projection to load only the userSetting field
     */
    public UserSetting getUserSettingByUserId(String userId) {
        log.info("Fetching UserSetting for userId: {}", userId);
        Query query = new Query(Criteria.where("_id").is(new ObjectId(userId)));
        query.fields().include(USER_SETTING_FIELD);

        var user = mongoTemplate.findOne(query, org.bson.Document.class, USER_COLLECTION);
        if (user == null) {
            log.error("User not found with userId: {}", userId);
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        log.debug("Retrieved document: {}", user);

        Object settingsObj = user.get(USER_SETTING_FIELD);
        if (settingsObj == null) {
            log.warn("UserSetting field is null for userId: {}, returning default settings", userId);
            return new UserSetting();
        }

        log.debug("UserSetting object type: {}, value: {}", settingsObj.getClass().getName(), settingsObj);

        try {
            UserSetting userSetting = mongoTemplate.getConverter().read(UserSetting.class,
                    (org.bson.Document) settingsObj);
            log.info("Successfully converted UserSetting for userId: {}", userId);
            return userSetting;
        } catch (Exception e) {
            log.error("Error converting UserSetting for userId: {}", userId, e);
            throw new AppException(ErrorCode.SYS_UNCATEGORIZED);
        }
    }

    /**
     * Update entire UserSetting
     */
    public boolean updateUserSetting(String userId, UserSetting userSetting) {
        log.info("Updating entire UserSetting for userId: {}", userId);
        Query query = new Query(Criteria.where("_id").is(new ObjectId(userId)));
        Update update = new Update().set(USER_SETTING_FIELD, userSetting);

        UpdateResult result = mongoTemplate.updateFirst(query, update, USER_COLLECTION);
        return result.getModifiedCount() > 0;
    }

    /**
     * Update General Settings
     */
    public boolean updateGeneralSettings(String userId, UserSetting.LanguageAndInterface languageAndInterface) {
        return updateNestedSetting(userId, "languageAndInterface", languageAndInterface);
    }

    /**
     * Update Security Settings
     */
    public boolean updateSecuritySettings(String userId, UserSetting.AccountSecuritySettings securitySettings) {
        return updateNestedSetting(userId, "accountSecuritySettings", securitySettings);
    }

    /**
     * Update Privacy Settings
     */
    public boolean updatePrivacySettings(String userId, UserSetting.PrivacySettings privacySettings) {
        return updateNestedSetting(userId, "privacySettings", privacySettings);
    }

    /**
     * Update Sync Settings
     */
    public boolean updateSyncSettings(String userId, UserSetting.ContactSettings syncSettings) {
        return updateNestedSetting(userId, "contactSettings", syncSettings);
    }

    /**
     * Update Appearance Settings
     */
    public boolean updateAppearanceSettings(String userId, UserSetting.LanguageAndInterface appearanceSettings) {
        return updateNestedSetting(userId, "languageAndInterface", appearanceSettings);
    }

    /**
     * Update Message Settings
     */
    public boolean updateMessageSettings(String userId, UserSetting.MessageSettings messageSettings) {
        return updateNestedSetting(userId, "messageSettings", messageSettings);
    }

    /**
     * Update Notification Settings
     */
    public boolean updateNotificationSettings(String userId, UserSetting.NotificationSettings notificationSettings) {
        return updateNestedSetting(userId, "notificationSettings", notificationSettings);
    }

    /**
     * Update Utilities Settings
     */
    public boolean updateUtilitiesSettings(String userId, UserSetting.DataOnDeviceSettings utilitiesSettings) {
        return updateNestedSetting(userId, "dataOnDeviceSettings", utilitiesSettings);
    }

    /**
     * Generic method to update a nested setting using dot notation
     */
    private boolean updateNestedSetting(String userId, String settingName, Object settingValue) {
        log.info("Updating {} for userId: {}", settingName, userId);
        Query query = new Query(Criteria.where("_id").is(new ObjectId(userId)));
        Update update = new Update().set(USER_SETTING_FIELD + "." + settingName, settingValue);

        UpdateResult result = mongoTemplate.updateFirst(query, update, USER_COLLECTION);
        if (result.getMatchedCount() == 0) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return result.getModifiedCount() > 0;
    }

    /**
     * Update a whole settings section by name.
     */
    public boolean updateSettingSection(String userId, String sectionName, Object sectionValue) {
        return updateNestedSetting(userId, sectionName, sectionValue);
    }

    /**
     * Update a specific field within a nested setting using dot notation
     */
    public boolean updateSpecificField(String userId, String fieldPath, Object value) {
        log.info("Updating field {} for userId: {}", fieldPath, userId);
        Query query = new Query(Criteria.where("_id").is(new ObjectId(userId)));
        Update update = new Update().set(USER_SETTING_FIELD + "." + fieldPath, value);

        UpdateResult result = mongoTemplate.updateFirst(query, update, USER_COLLECTION);
        if (result.getMatchedCount() == 0) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return result.getModifiedCount() > 0;
    }

    /**
     * Get a specific nested setting
     */
    public <T> T getNestedSetting(String userId, String settingName, Class<T> settingClass) {
        log.info("Fetching {} for userId: {}", settingName, userId);
        Query query = new Query(Criteria.where("_id").is(new ObjectId(userId)));
        query.fields().include(USER_SETTING_FIELD + "." + settingName);

        var user = mongoTemplate.findOne(query, org.bson.Document.class, USER_COLLECTION);
        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }

        org.bson.Document userSetting = (org.bson.Document) user.get(USER_SETTING_FIELD);
        if (userSetting == null) {
            return null;
        }

        Object settingObj = userSetting.get(settingName);
        if (settingObj == null) {
            return null;
        }

        return mongoTemplate.getConverter().read(settingClass, (org.bson.Document) settingObj);
    }

    /**
     * Delete/Reset a specific nested setting to default
     */
    public boolean resetNestedSetting(String userId, String settingName, Object defaultValue) {
        log.info("Resetting {} to default for userId: {}", settingName, userId);
        return updateNestedSetting(userId, settingName, defaultValue);
    }

    public List<UserSearchVisibilityResponse> getSearchVisibilityByUserIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ObjectId> objectIds = userIds.stream()
                .filter(Objects::nonNull)
                .filter(ObjectId::isValid)
                .distinct()
                .map(ObjectId::new)
                .toList();

        if (objectIds.isEmpty()) {
            return Collections.emptyList();
        }

        Query query = new Query(Criteria.where("_id").in(objectIds));
        query.fields()
                .include(USER_SETTING_FIELD + ".privacySettings.nameSearchVisibility")
                .include(USER_SETTING_FIELD + ".privacySettings.phoneSearchVisibility");

        return mongoTemplate.find(query, Document.class, USER_COLLECTION).stream()
                .map(this::toUserSearchVisibilityResponse)
                .toList();
    }

    private UserSearchVisibilityResponse toUserSearchVisibilityResponse(Document user) {
        ObjectId userId = user.getObjectId("_id");
        Document userSetting = user.get(USER_SETTING_FIELD, Document.class);
        Document privacySettings = userSetting != null ? userSetting.get("privacySettings", Document.class) : null;

        return UserSearchVisibilityResponse.builder()
                .userId(userId != null ? userId.toHexString() : null)
                .nameSearchVisibility(readNameSearchVisibility(privacySettings))
                .phoneSearchVisibility(readSearchVisibility(privacySettings, "phoneSearchVisibility"))
                .build();
    }

    private SearchVisibility readNameSearchVisibility(Document privacySettings) {
        SearchVisibility searchVisibility = readSearchVisibility(privacySettings, "nameSearchVisibility");
        return searchVisibility == SearchVisibility.NONE ? SearchVisibility.PUBLIC : searchVisibility;
    }

    private SearchVisibility readSearchVisibility(Document privacySettings, String fieldName) {
        if (privacySettings == null) {
            return SearchVisibility.PUBLIC;
        }

        Object value = privacySettings.get(fieldName);
        if (value instanceof SearchVisibility searchVisibility) {
            return searchVisibility;
        }
        if (value instanceof String searchVisibility) {
            try {
                return SearchVisibility.valueOf(searchVisibility);
            } catch (IllegalArgumentException ignored) {
                log.warn("Unknown search visibility value {} for field {}", searchVisibility, fieldName);
            }
        }
        return SearchVisibility.PUBLIC;
    }
}
