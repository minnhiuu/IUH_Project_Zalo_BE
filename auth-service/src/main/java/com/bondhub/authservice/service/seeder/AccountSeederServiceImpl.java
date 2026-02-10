package com.bondhub.authservice.service.seeder;

import com.bondhub.authservice.model.Account;
import com.bondhub.authservice.repository.AccountRepository;
import com.bondhub.common.enums.Role;
import com.bondhub.common.event.account.AccountRegisteredEvent;
import com.bondhub.common.model.kafka.EventType;
import com.bondhub.common.publisher.OutboxEventPublisher;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AccountSeederServiceImpl implements AccountSeederService {

    AccountRepository accountRepository;
    PasswordEncoder passwordEncoder;
    OutboxEventPublisher outboxEventPublisher;

    private static final String[] FUZZY_NAMES = {
            // Test fuzzy search với "Huyền"
            "Trần Ngọc Huyền", "Trần Ngọc Huyên", "Trần Ngoc Huyền", "Tran Ngọc Huyền",
            "Võ Ngọc Huyền", "Nguyễn Ngọc Huyền", "Lê Ngọc Huyền", "Phạm Ngọc Huyền",
            "Trần Thu Huyền", "Nguyễn Thị Huyền", "Lê Thanh Huyền",
            
            // Tên phổ biến Việt Nam - Nữ
            "Nguyễn Thị Mai", "Trần Thanh Hương", "Lê Hồng Nhung", "Phạm Thu Thảo",
            "Hoàng Minh Anh", "Vũ Thanh Tâm", "Đỗ Hải Yến", "Bùi Khánh Linh",
            "Phan Thùy Dương", "Lý Thu Hà", "Đinh Ngọc Lan", "Ngô Phương Anh",
            "Đặng Thu Hương", "Võ Thanh Nga", "Trương Thu Hiền", "Mai Quỳnh Anh",
            "Dương Thùy Linh", "Tô Hồng Nhung", "Cao Minh Châu", "Lâm Thanh Tâm",
            
            // Tên phổ biến Việt Nam - Nam
            "Nguyễn Văn An", "Trần Đức Anh", "Lê Quang Dũng", "Phạm Minh Tuấn",
            "Hoàng Văn Hùng", "Vũ Đức Thắng", "Đỗ Minh Quân", "Bùi Xuân Trường",
            "Phan Văn Long", "Lý Quốc Bảo", "Đinh Tiến Đạt", "Ngô Văn Thành",
            "Đặng Quốc Huy", "Võ Hoàng Nam", "Trương Minh Khoa", "Mai Xuân Phong",
            "Dương Văn Tùng", "Tô Minh Nhật", "Cao Quang Hải", "Lâm Hoàng Long",
            
            // Tên 2 chữ đệm
            "Nguyễn Ngọc Bảo Anh", "Trần Thị Thanh Mai", "Lê Nguyễn Minh An",
            "Phạm Hoàng Gia Bảo", "Võ Thị Hồng Nhung", "Hoàng Lê Minh Tuấn",
            
            // Tên hiện đại
            "Khánh An", "Minh Anh", "Hoàng Long", "Bảo Ngọc", "Thu Trang",
            "Hải Đăng", "Quỳnh Như", "Gia Bảo", "Phương Thảo", "Tuấn Kiệt",
            
            // Tên có âm đặc biệt (để test fuzzy)
            "Nguyễn Đình Trọng", "Trần Nhật Minh", "Lê Thiện Nhân", "Phạm Thế Vinh",
            "Hoàng Quốc Việt", "Vũ Đăng Khoa", "Đỗ Tiến Dũng", "Bùi Quang Huy",
            
            // Tên miền Bắc
            "Nguyễn Thu Hiền", "Trần Văn Đức", "Lê Thị Hằng", "Phạm Văn Nam",
            "Hoàng Thị Nga", "Vũ Văn Hải", "Đỗ Thị Thanh", "Bùi Văn Sơn",
            
            // Tên miền Trung
            "Nguyễn Tấn Phát", "Trần Thị Xuân", "Lê Văn Tâm", "Phạm Thị Hương",
            "Hoàng Văn Lâm", "Vũ Thị Lan", "Đỗ Văn Khải", "Bùi Thị Hoa",
            
            // Tên miền Nam
            "Nguyễn Thành Đạt", "Trần Thị Kim", "Lê Văn Phúc", "Phạm Thị Thu",
            "Hoàng Văn Tài", "Vũ Thị Ngọc", "Đỗ Văn Sang", "Bùi Thị Phương",
            
            // Tên có dấu đặc biệt
            "Nguyễn Thị Mỹ Duyên", "Trần Quốc Khánh", "Lê Thị Diệu", "Phạm Tuấn Kiệt",
            "Hoàng Gia Huy", "Vũ Thị Thúy", "Đỗ Anh Tuấn", "Bùi Ngọc Quỳnh"
    };

    @Override
    public Map<String, Object> seedAccounts(int count) {
        log.info("🌱 Starting seed {} accounts with fuzzy names...", count);

        int created = 0;
        int skipped = 0;
        Random random = new Random();

        created += createAdminAccount();

        int startIndex = findNextAvailableIndex();
        log.info("📊 Starting from testuser{}", startIndex);

        for (int i = 0; i < count; i++) {
            try {
                int currentIndex = startIndex + i;
                String email = String.format("testuser%d@bondhub.com", currentIndex);
                String phoneNumber = String.format("09%08d", 10000000 + currentIndex);

                String fullName;
                if (random.nextBoolean()) {
                    int nameIndex = random.nextInt(FUZZY_NAMES.length);
                    String baseName = FUZZY_NAMES[nameIndex];
                    fullName = applyFuzzyVariation(baseName, random);
                } else {
                    fullName = generateFuzzyName(random);
                }

                if (accountRepository.existsByEmail(email)) {
                    log.debug("⏭️  Account already exists: {}", email);
                    skipped++;
                    continue;
                }

                Account account = Account.builder()
                        .email(email)
                        .password(passwordEncoder.encode("Test@123"))
                        .phoneNumber(phoneNumber)
                        .role(Role.USER)
                        .isVerified(true)
                        .enabled(true)
                        .build();

                account = accountRepository.save(account);
                log.info("✅ Account created: {} - {}", email, fullName);

                AccountRegisteredEvent event = AccountRegisteredEvent.builder()
                        .accountId(account.getId())
                        .email(account.getEmail())
                        .fullName(fullName)
                        .phoneNumber(account.getPhoneNumber())
                        .timestamp(System.currentTimeMillis())
                        .build();

                outboxEventPublisher.saveAndPublish(
                        account.getId(),
                        "Account",
                        EventType.ACCOUNT_REGISTERED,
                        event
                );

                log.info("📤 Event published for: {} - {}", email, fullName);
                created++;

                Thread.sleep(50);

            } catch (Exception e) {
                log.error("❌ Failed to seed account #{}", i, e);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        result.put("total", count);
        result.put("message", String.format("Seed completed! Created: %d, Skipped: %d (including 1 admin)", created, skipped));
        result.put("fuzzy_test_info", "100+ diverse Vietnamese names for comprehensive search testing");
        result.put("admin_credentials", "Email: admin@bondhub.com | Password: Admin@123");
        result.put("test_accounts_password", "Test@123");
        result.put("next_start_index", startIndex + count);

        log.info("🌱 Seeding completed! Created: {}, Skipped: {}", created, skipped);
        return result;
    }

    private int findNextAvailableIndex() {
        int index = 1;
        while (accountRepository.existsByEmail(String.format("testuser%d@bondhub.com", index))) {
            index++;
            if (index > 100000) {
                break;
            }
        }
        return index;
    }

    private int createAdminAccount() {
        String adminEmail = "admin@bondhub.com";

        try {
            if (accountRepository.existsByEmail(adminEmail)) {
                log.info("👤 Admin account already exists: {}", adminEmail);
                return 0;
            }

            Account adminAccount = Account.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode("Admin@123"))
                    .phoneNumber("0900000000")
                    .role(Role.ADMIN)
                    .isVerified(true)
                    .enabled(true)
                    .build();

            adminAccount = accountRepository.save(adminAccount);
            log.info("👑 Admin account created: {}", adminEmail);
            log.info("ℹ️ Admin account does not trigger user creation event (admin-only account)");
            return 1;

        } catch (Exception e) {
            log.error("❌ Failed to create admin account", e);
            return 0;
        }
    }

    /**
     * Apply fuzzy variations lên tên có sẵn
     */
    private String applyFuzzyVariation(String baseName, Random random) {
        int variation = random.nextInt(8);
        
        switch (variation) {
            case 0: // Giữ nguyên
                return baseName;
            case 1: // Mất dấu toàn bộ
                return removeAllAccents(baseName);
            case 2: // Mất dấu một phần
                return removeSomeAccents(baseName, random);
            case 3: // Lowercase
                return baseName.toLowerCase();
            case 4: // UPPERCASE
                return baseName.toUpperCase();
            case 5: // Đảo ngược thứ tự từ
                String[] parts = baseName.split(" ");
                if (parts.length >= 2) {
                    return parts[parts.length - 1] + " " + String.join(" ", java.util.Arrays.copyOfRange(parts, 0, parts.length - 1));
                }
                return baseName;
            case 6: // Thêm/bớt space
                return baseName.replaceAll(" ", random.nextBoolean() ? "  " : " ").trim();
            default: // Mix uppercase/lowercase từng từ
                String[] words = baseName.split(" ");
                StringBuilder result = new StringBuilder();
                for (String word : words) {
                    if (random.nextBoolean()) {
                        result.append(word.toUpperCase());
                    } else {
                        result.append(word.toLowerCase());
                    }
                    result.append(" ");
                }
                return result.toString().trim();
        }
    }

    private String generateFuzzyName(Random random) {
        String[] firstNames = {"Trần", "Nguyễn", "Lê", "Phạm", "Hoàng", "Võ", "Phan", "Vũ", "Đặng", "Bùi", "Đỗ", 
                               "Lý", "Đinh", "Ngô", "Dương", "Trương", "Cao", "Lâm", "Tô", "Mai"};
        String[] middleNames = {"Văn", "Thị", "Đức", "Ngọc", "Thu", "Minh", "Thanh", "Quốc", "Hồng", "Ánh", 
                                "Thái", "Hoàng", "Xuân", "Hải", "Thùy", "Khánh", "Gia", "Bảo", "Quỳnh", "Tiến"};
        String[] lastNames = {"An", "Anh", "Bình", "Châu", "Dũng", "Hà", "Hùng", "Linh", "Mai", "Nam", 
                              "Phong", "Quân", "Tâm", "Tuấn", "Hương", "Lan", "Nga", "Thảo", "Yến", "Khoa",
                              "Long", "Đạt", "Huy", "Thành", "Phúc", "Tài", "Kiệt", "Nhân", "Khải", "Sang"};
        
        String firstName = firstNames[random.nextInt(firstNames.length)];
        String middleName = middleNames[random.nextInt(middleNames.length)];
        String lastName = lastNames[random.nextInt(lastNames.length)];
        
        int nameStyle = random.nextInt(10); // 10 styles khác nhau
        String result;
        
        switch (nameStyle) {
            case 0: // Chuẩn: Họ + Đệm + Tên
                result = firstName + " " + middleName + " " + lastName;
                break;
            case 1: // Thiếu đệm: Họ + Tên
                result = firstName + " " + lastName;
                break;
            case 2: // Đảo ngược: Tên + Đệm + Họ (sai)
                result = lastName + " " + middleName + " " + firstName;
                break;
            case 3: // Chỉ có Tên + Họ (đảo)
                result = lastName + " " + firstName;
                break;
            case 4: // Lowercase toàn bộ
                result = (firstName + " " + middleName + " " + lastName).toLowerCase();
                break;
            case 5: // UPPERCASE toàn bộ
                result = (firstName + " " + middleName + " " + lastName).toUpperCase();
                break;
            case 6: // Mất dấu ngẫu nhiên
                result = removeSomeAccents(firstName + " " + middleName + " " + lastName, random);
                break;
            case 7: // Mất dấu toàn bộ
                result = removeAllAccents(firstName + " " + middleName + " " + lastName);
                break;
            case 8: // Đảo họ và tên, giữ đệm giữa
                result = lastName + " " + middleName + " " + firstName;
                break;
            default: // Bình thường nhưng có thể thiếu hoặc thừa space
                boolean hasMiddle = random.nextBoolean();
                if (hasMiddle) {
                    result = firstName + " " + middleName + " " + lastName;
                } else {
                    result = firstName + " " + lastName;
                }
                // Thêm hoặc bớt space ngẫu nhiên
                if (random.nextInt(5) == 0) {
                    result = result.replaceAll(" ", "  "); // Double space
                }
                break;
        }
        
        return result.trim();
    }
    
    private String removeSomeAccents(String text, Random random) {
        String[][] accentMap = {
            {"á", "a"}, {"à", "a"}, {"ả", "a"}, {"ã", "a"}, {"ạ", "a"},
            {"ă", "a"}, {"ắ", "a"}, {"ằ", "a"}, {"ẳ", "a"}, {"ẵ", "a"}, {"ặ", "a"},
            {"â", "a"}, {"ấ", "a"}, {"ầ", "a"}, {"ẩ", "a"}, {"ẫ", "a"}, {"ậ", "a"},
            {"é", "e"}, {"è", "e"}, {"ẻ", "e"}, {"ẽ", "e"}, {"ẹ", "e"},
            {"ê", "e"}, {"ế", "e"}, {"ề", "e"}, {"ể", "e"}, {"ễ", "e"}, {"ệ", "e"},
            {"í", "i"}, {"ì", "i"}, {"ỉ", "i"}, {"ĩ", "i"}, {"ị", "i"},
            {"ó", "o"}, {"ò", "o"}, {"ỏ", "o"}, {"õ", "o"}, {"ọ", "o"},
            {"ô", "o"}, {"ố", "o"}, {"ồ", "o"}, {"ổ", "o"}, {"ỗ", "o"}, {"ộ", "o"},
            {"ơ", "o"}, {"ớ", "o"}, {"ờ", "o"}, {"ở", "o"}, {"ỡ", "o"}, {"ợ", "o"},
            {"ú", "u"}, {"ù", "u"}, {"ủ", "u"}, {"ũ", "u"}, {"ụ", "u"},
            {"ư", "u"}, {"ứ", "u"}, {"ừ", "u"}, {"ử", "u"}, {"ữ", "u"}, {"ự", "u"},
            {"ý", "y"}, {"ỳ", "y"}, {"ỷ", "y"}, {"ỹ", "y"}, {"ỵ", "y"},
            {"đ", "d"}, {"Đ", "D"}
        };
        
        String result = text;
        // Chỉ mất dấu một vài chữ ngẫu nhiên (50% chance mỗi chữ)
        for (String[] pair : accentMap) {
            if (random.nextBoolean()) {
                result = result.replaceAll(pair[0], pair[1]);
                result = result.replaceAll(pair[0].toUpperCase(), pair[1].toUpperCase());
            }
        }
        return result;
    }
    
    private String removeAllAccents(String text) {
        String[][] accentMap = {
            {"á|à|ả|ã|ạ|ă|ắ|ằ|ẳ|ẵ|ặ|â|ấ|ầ|ẩ|ẫ|ậ", "a"},
            {"Á|À|Ả|Ã|Ạ|Ă|Ắ|Ằ|Ẳ|Ẵ|Ặ|Â|Ấ|Ầ|Ẩ|Ẫ|Ậ", "A"},
            {"é|è|ẻ|ẽ|ẹ|ê|ế|ề|ể|ễ|ệ", "e"},
            {"É|È|Ẻ|Ẽ|Ẹ|Ê|Ế|Ề|Ể|Ễ|Ệ", "E"},
            {"í|ì|ỉ|ĩ|ị", "i"},
            {"Í|Ì|Ỉ|Ĩ|Ị", "I"},
            {"ó|ò|ỏ|õ|ọ|ô|ố|ồ|ổ|ỗ|ộ|ơ|ớ|ờ|ở|ỡ|ợ", "o"},
            {"Ó|Ò|Ỏ|Õ|Ọ|Ô|Ố|Ồ|Ổ|Ỗ|Ộ|Ơ|Ớ|Ờ|Ở|Ỡ|Ợ", "O"},
            {"ú|ù|ủ|ũ|ụ|ư|ứ|ừ|ử|ữ|ự", "u"},
            {"Ú|Ù|Ủ|Ũ|Ụ|Ư|Ứ|Ừ|Ử|Ữ|Ự", "U"},
            {"ý|ỳ|ỷ|ỹ|ỵ", "y"},
            {"Ý|Ỳ|Ỷ|Ỹ|Ỵ", "Y"},
            {"đ", "d"},
            {"Đ", "D"}
        };
        
        String result = text;
        for (String[] pair : accentMap) {
            result = result.replaceAll(pair[0], pair[1]);
        }
        return result;
    }
}
