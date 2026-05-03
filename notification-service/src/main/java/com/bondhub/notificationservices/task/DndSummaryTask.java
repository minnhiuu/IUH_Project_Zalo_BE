package com.bondhub.notificationservices.task;

import com.bondhub.notificationservices.model.UserDevice;
import com.bondhub.notificationservices.repository.UserDeviceRepository;
import com.bondhub.notificationservices.service.dnd.DndSummaryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DndSummaryTask {

    UserDeviceRepository userDeviceRepository;
    DndSummaryService dndSummaryService;

    @Scheduled(fixedRate = 60000)
    public void sendDndSummaries() {
        List<UserDevice> devices = userDeviceRepository.findAll();

        Set<String> userIdsToSummarize = new HashSet<>();

        for (UserDevice device : devices) {
            if (!device.isDndEnabled()) {
                continue;
            }

            if (device.getDndStartTime() == null || device.getDndEndTime() == null) {
                continue;
            }

            if (hasJustExitedDnd(device)) {
                log.info("[DND Summary Task] User {} has just exited Dnd mode on device {}! Triggering summary...", 
                        device.getUserId(), device.getDeviceId());
                userIdsToSummarize.add(device.getUserId());
            }
        }

        for (String userId : userIdsToSummarize) {
            try {
                dndSummaryService.sendSummaryForUser(userId);
            } catch (Exception e) {
                log.error("[DND Summary Task] Failed for user={}: {}",
                        userId,
                        e.getMessage(),
                        e);
            }
        }
    }

    private boolean hasJustExitedDnd(UserDevice device) {
        try {
            String timezone = device.getDndTimezone() != null
                    ? device.getDndTimezone()
                    : "Asia/Ho_Chi_Minh";

            // Normalize "GMT+X" or "GMT-X" to "GMT+0X:00" to avoid DateTimeException
            if (timezone.startsWith("GMT") && (timezone.contains("+") || timezone.contains("-"))) {
                String prefix = timezone.substring(0, 4); // "GMT+" or "GMT-"
                String offset = timezone.substring(4);   // "7" or "7:00"
                if (!offset.contains(":")) {
                    if (offset.length() == 1) offset = "0" + offset + ":00";
                    else if (offset.length() == 2) offset = offset + ":00";
                } else {
                    String[] parts = offset.split(":");
                    if (parts[0].length() == 1) offset = "0" + offset;
                }
                timezone = prefix + offset;
            }

            ZoneId zoneId = ZoneId.of(timezone);
            ZonedDateTime nowZoned = ZonedDateTime.now(zoneId);

            LocalTime now = nowZoned.toLocalTime();
            LocalTime end = LocalTime.parse(device.getDndEndTime());

            long diffSeconds = Math.abs(Duration.between(end, now).getSeconds());

            boolean justPassedEndTime = diffSeconds <= 60;

            if (!justPassedEndTime) {
                return false;
            }

            LocalTime start = LocalTime.parse(device.getDndStartTime());

            if (start.isBefore(end)) {
                return isActiveDay(device, nowZoned.getDayOfWeek());
            }

            DayOfWeek dndStartDay = nowZoned.minusDays(1).getDayOfWeek();

            return isActiveDay(device, dndStartDay);
        } catch (Exception e) {
            log.warn("[DND Summary Task] Failed to check device={}, error={}",
                    device.getDeviceId(),
                    e.getMessage());
            return false;
        }
    }

    private boolean isActiveDay(UserDevice device, DayOfWeek day) {
        return device.getActiveDays() == null
                || device.getActiveDays().isEmpty()
                || device.getActiveDays().contains(day);
    }
}
