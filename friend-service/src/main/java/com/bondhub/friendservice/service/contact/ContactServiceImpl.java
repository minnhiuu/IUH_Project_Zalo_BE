package com.bondhub.friendservice.service.contact;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.friendservice.client.UserServiceClient;
import com.bondhub.friendservice.dto.request.ContactImportRequest;
import com.bondhub.friendservice.dto.response.ContactImportResponse;
import com.bondhub.friendservice.graph.service.GraphFriendService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    UserServiceClient userServiceClient;
    GraphFriendService graphFriendService;
    SecurityUtil securityUtil;

    @Override
    public ContactImportResponse importContacts(ContactImportRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        log.info("Importing {} contacts for user: {}", request.contacts().size(), currentUserId);

        // 1. Normalize phone numbers and collect emails
        Set<String> normalizedPhones = new LinkedHashSet<>();
        Set<String> normalizedEmails = new LinkedHashSet<>();

        for (ContactImportRequest.ContactEntry contact : request.contacts()) {
            if (contact.phones() != null) {
                contact.phones().stream()
                        .map(this::normalizePhoneNumber)
                        .filter(Objects::nonNull)
                        .forEach(normalizedPhones::add);
            }
            if (contact.emails() != null) {
                contact.emails().stream()
                        .filter(e -> e != null && !e.isBlank())
                        .map(String::toLowerCase)
                        .map(String::trim)
                        .forEach(normalizedEmails::add);
            }
        }

        log.info("Normalized {} phones and {} emails for user {}", normalizedPhones.size(), normalizedEmails.size(), currentUserId);

        // 2. Match with user-service
        Map<String, String> phoneToUserId = new HashMap<>(); // phone -> userId
        Map<String, String> emailToUserId = new HashMap<>(); // email -> userId

        if (!normalizedPhones.isEmpty()) {
            try {
                ApiResponse<List<UserSummaryResponse>> phoneResponse = userServiceClient.findUsersByPhones(new ArrayList<>(normalizedPhones));
                if (phoneResponse != null && phoneResponse.data() != null) {
                    for (UserSummaryResponse user : phoneResponse.data()) {
                        if (user.phoneNumber() != null) {
                            phoneToUserId.put(user.phoneNumber(), user.id());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to match contacts by phone for user {}: {}", currentUserId, e.getMessage());
            }
        }

        if (!normalizedEmails.isEmpty()) {
            try {
                ApiResponse<List<UserSummaryResponse>> emailResponse = userServiceClient.findUsersByEmails(new ArrayList<>(normalizedEmails));
                if (emailResponse != null && emailResponse.data() != null) {
                    for (UserSummaryResponse user : emailResponse.data()) {
                        // UserSummaryResponse doesn't have email, so we use the id directly
                        emailToUserId.put(user.id(), user.id());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to match contacts by email for user {}: {}", currentUserId, e.getMessage());
            }
        }

        // 3. Collect all matched user IDs (deduplicated)
        Set<String> matchedUserIds = new LinkedHashSet<>();
        matchedUserIds.addAll(phoneToUserId.values());
        matchedUserIds.addAll(emailToUserId.values());
        // Remove self
        matchedUserIds.remove(currentUserId);

        log.info("Matched {} unique users from contacts for user {}", matchedUserIds.size(), currentUserId);

        // 4. Ensure current user node exists in Neo4j
        graphFriendService.ensureUserNode(currentUserId);

        // 5. Create IN_CONTACT relationships with scoring
        int relationsCreated = 0;

        // Phone matches: score = 1.0
        for (String matchedUserId : phoneToUserId.values()) {
            if (!matchedUserId.equals(currentUserId)) {
                graphFriendService.mergeInContact(currentUserId, matchedUserId, 1.0, "PHONE");
                relationsCreated++;
            }
        }

        // Email matches: score = 0.8
        for (String matchedUserId : emailToUserId.values()) {
            if (!matchedUserId.equals(currentUserId)) {
                graphFriendService.mergeInContact(currentUserId, matchedUserId, 0.8, "EMAIL");
                relationsCreated++;
            }
        }

        log.info("Created/merged {} IN_CONTACT relationships for user {}", relationsCreated, currentUserId);

        return ContactImportResponse.builder()
                .totalContacts(request.contacts().size())
                .normalizedPhones(normalizedPhones.size())
                .normalizedEmails(normalizedEmails.size())
                .matchedUsers(matchedUserIds.size())
                .contactRelationsCreated(relationsCreated)
                .matchedUserIds(new ArrayList<>(matchedUserIds))
                .build();
    }

    /**
     * Normalize phone number to E.164 format (Vietnam +84).
     * Returns null if the phone number is invalid.
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) return null;

        // Strip all non-digit and non-plus characters
        String digits = phone.replaceAll("[^0-9+]", "");

        if (digits.startsWith("+84") && digits.length() >= 12) {
            return digits;
        }
        if (digits.startsWith("84") && digits.length() >= 11) {
            return "+" + digits;
        }
        if (digits.startsWith("0") && digits.length() >= 10) {
            return "+84" + digits.substring(1);
        }

        // Unknown format — return null
        return null;
    }
}
