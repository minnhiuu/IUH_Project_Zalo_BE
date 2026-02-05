package com.bondhub.userservice.service.user;

import com.bondhub.userservice.model.User;
import com.bondhub.userservice.model.elasticsearch.UserIndex;
import com.bondhub.userservice.model.enums.Gender;
import com.bondhub.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSeederService {

    private final UserRepository userRepository;
    private final UserSearchService userSearchService;

    private static final String[] TEST_NAMES = {
            "Trần Thái Hà", "Võ Ngọc Huyền Trân", "Nguyễn Đức Trí",
            "Lê Như Phước", "Thanh Trà", "Trần Thành Tài",
            "Phạm Quỳnh Anh", "Bùi Ngọc Trâm", "Nguyễn Hoàng An"
    };

    public String seedUsers(int count) {
        Random random = new Random();
        int successCount = 0;

        for (int i = 0; i < count; i++) {
            try {
                String fullName = (i < TEST_NAMES.length) ? TEST_NAMES[i] : generateRandomName(random);

                String accountId = UUID.randomUUID().toString();
                String phoneNumber = "09" + String.format("%08d", random.nextInt(100000000));

                User user = User.builder()
                        .fullName(fullName)
                        .accountId(accountId)
                        .gender(random.nextBoolean() ? Gender.MALE : Gender.FEMALE)
                        .build();

                user = userRepository.save(user);

                UserIndex userIndex = UserIndex.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .phoneNumber(phoneNumber)
                        .accountId(accountId)
                        .build();

                userSearchService.saveToToIndex(userIndex);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to seed user at index {}", i, e);
            }
        }
        return "Seeded " + successCount + " users.";
    }

    private String generateRandomName(Random random) {
        String[] first = {"Nguyễn", "Lê", "Phạm", "Phan", "Vũ"};
        String[] mid = {"Văn", "Thị", "Minh", "Anh"};
        String[] last = {"Hùng", "Lan", "Hải", "Tuấn", "Trà"};
        return first[random.nextInt(first.length)] + " " +
                mid[random.nextInt(mid.length)] + " " +
                last[random.nextInt(last.length)];
    }
}
