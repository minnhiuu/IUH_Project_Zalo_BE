package com.bondhub.authservice.service.otp;

import com.bondhub.authservice.enums.OtpPurpose;
import com.bondhub.authservice.model.redis.OtpCooldown;
import com.bondhub.authservice.model.redis.OtpRecord;
import com.bondhub.authservice.repository.redis.OtpCooldownRepository;
import com.bondhub.authservice.repository.redis.OtpRepository;
import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {

    private final OtpRepository otpRepository;
    private final OtpCooldownRepository otpCooldownRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${otp.ttl-seconds:300}")
    private long otpTtlSeconds;

    @Value("${otp.max-attempts:5}")
    private int maxAttempts;

    @Value("${otp.cooldown-seconds:60}")
    private long cooldownSeconds;

    @Override
    public String generateAndStoreOtp(String email, OtpPurpose purpose) {
        log.info("Generating OTP for email: {}, purpose: {}", email, purpose);

        // Check cooldown - prevent rapid OTP resend requests
        if (otpCooldownRepository.existsById(email)) {
            log.warn("🚨 [SECURITY] OTP generation blocked due to cooldown for email: {}", email);
            throw new AppException(ErrorCode.OTP_COOLDOWN_ACTIVE);
        }

        // Generate random 6-digit OTP
        String otp = generateRandomOtp();

        // Hash the OTP before storing
        String otpHash = hashOtp(otp);

        long now = System.currentTimeMillis();
        long expiresAt = now + (otpTtlSeconds * 1000);

        // Create OTP record
        OtpRecord otpRecord = OtpRecord.builder()
                .email(email)
                .otpHash(otpHash)
                .purpose(purpose)
                .attempts(0)
                .createdAt(now)
                .expiresAt(expiresAt)
                .ttl(otpTtlSeconds)
                .build();

        // Save to Redis (replaces any existing OTP for this email)
        otpRepository.save(otpRecord);

        // Set cooldown to prevent immediate resend
        OtpCooldown cooldown = OtpCooldown.builder()
                .email(email)
                .cooldownSetAt(now)
                .ttl(cooldownSeconds)
                .build();
        otpCooldownRepository.save(cooldown);

        log.info("✅ OTP generated and stored for email: {}, purpose: {}, expires in {} seconds",
                email, purpose, otpTtlSeconds);

        // Return plaintext OTP to caller (for sending via email)
        return otp;
    }

    @Override
    public boolean validateOtp(String email, String otp, OtpPurpose purpose) {
        log.info("Validating OTP for email: {}, purpose: {}", email, purpose);

        // Fetch OTP record from Redis
        return otpRepository.findById(email)
                .map(record -> {
                    // Check if OTP has expired
                    if (!record.isValid()) {
                        log.warn("❌ OTP expired for email: {}", email);
                        otpRepository.delete(record);
                        return false;
                    }

                    // Check if purpose matches
                    if (!purpose.equals(record.getPurpose())) {
                        log.warn("❌ OTP purpose mismatch for email: {}. Expected: {}, Got: {}",
                                email, record.getPurpose(), purpose);
                        return false;
                    }

                    // Check if max attempts exceeded
                    if (record.getAttempts() >= maxAttempts) {
                        log.error("🚨 [SECURITY] Max OTP attempts exceeded for email: {}", email);
                        otpRepository.delete(record);
                        return false;
                    }

                    // Hash provided OTP and compare with stored hash
                    String providedHash = hashOtp(otp);
                    if (!providedHash.equals(record.getOtpHash())) {
                        // Increment attempts counter
                        record.setAttempts(record.getAttempts() + 1);

                        if (record.getAttempts() >= maxAttempts) {
                            log.error("🚨 [SECURITY] Max OTP attempts reached for email: {}. Deleting OTP.", email);
                            otpRepository.delete(record);
                        } else {
                            otpRepository.save(record);
                            log.warn("❌ Invalid OTP for email: {}. Attempts: {}/{}",
                                    email, record.getAttempts(), maxAttempts);
                        }
                        return false;
                    }

                    // OTP is valid - delete it (one-time use)
                    otpRepository.delete(record);
                    log.info("✅ OTP validated successfully for email: {}. OTP deleted.", email);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("❌ No OTP found for email: {}", email);
                    return false;
                });
    }

    @Override
    public void invalidateOtp(String email) {
        otpRepository.findById(email).ifPresent(record -> {
            otpRepository.delete(record);
            log.info("OTP invalidated for email: {}", email);
        });
    }

    @Override
    public String hashOtp(String otp) {
        if (otp == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generate a random OTP of specified length
     */
    private String generateRandomOtp() {
        int bound = (int) Math.pow(10, otpLength);
        int randomNumber = secureRandom.nextInt(bound);
        return String.format("%0" + otpLength + "d", randomNumber);
    }
}
