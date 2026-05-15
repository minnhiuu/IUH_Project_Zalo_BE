package com.bondhub.searchservice.service.searchevent;

import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.searchservice.dto.request.SearchEventRequest;
import com.bondhub.searchservice.model.mongodb.SearchEvent;
import com.bondhub.searchservice.repository.mongodb.SearchEventRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SearchEventServiceImpl implements SearchEventService {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    SearchEventRepository searchEventRepository;
    SecurityUtil securityUtil;

    @Override
    public void record(SearchEventRequest request) {
        String currentUserId = securityUtil.getCurrentUserId();
        String keyword = request.keyword().trim();

        SearchEvent event = SearchEvent.builder()
                .userId(currentUserId)
                .keyword(keyword)
                .normalizedKeyword(normalizeKeyword(keyword))
                .targetUserId(request.targetUserId())
                .rank(request.rank())
                .eventType(request.eventType())
                .createdAt(Instant.now())
                .build();

        searchEventRepository.save(event);
        log.debug("Recorded search event userId={}, targetUserId={}, eventType={}, rank={}",
                currentUserId, request.targetUserId(), request.eventType(), request.rank());
    }

    private String normalizeKeyword(String keyword) {
        String normalized = Normalizer.normalize(keyword, Normalizer.Form.NFD)
                .replace('Đ', 'D')
                .replace('đ', 'd');
        return WHITESPACE_PATTERN.matcher(
                        DIACRITICS_PATTERN.matcher(normalized)
                                .replaceAll("")
                                .toLowerCase(Locale.ROOT)
                                .trim())
                .replaceAll(" ");
    }
}
