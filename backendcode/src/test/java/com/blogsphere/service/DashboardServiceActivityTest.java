package com.blogsphere.service;

import com.blogsphere.entity.Notification;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardServiceActivityTest {

    @Test
    void weeklyActivityIncludesRecordedDaysAndZeroFillsTheSevenDayWindow() {
        LocalDate firstDay = LocalDate.of(2026, 11, 19);
        List<Notification> notifications = List.of(
                notificationAt("2026-11-20T00:00:00"),
                notificationAt("2026-11-20T23:59:59"),
                notificationAt("2026-11-24T00:00:00"),
                notificationAt("2026-11-18T23:59:59"),
                notificationAt("2026-11-26T00:00:00")
        );

        Map<String, Long> activity = DashboardService.buildWeeklyActivity(notifications, firstDay);

        assertEquals(Map.of(
                "Nov 19", 0L,
                "Nov 20", 2L,
                "Nov 21", 0L,
                "Nov 22", 0L,
                "Nov 23", 0L,
                "Nov 24", 1L,
                "Nov 25", 0L
        ), activity);
        assertEquals(List.of("Nov 19", "Nov 20", "Nov 21", "Nov 22", "Nov 23", "Nov 24", "Nov 25"),
                List.copyOf(activity.keySet()));
    }

    @Test
    void kolkataDateDefinesTheCalendarWindowAcrossUtcMidnight() {
        LocalDate todayInKolkata = LocalDateTime.of(2026, 11, 24, 18, 31)
                .atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                .toLocalDate();

        assertEquals(LocalDate.of(2026, 11, 25), todayInKolkata);
        assertEquals(LocalDate.of(2026, 11, 19), todayInKolkata.minusDays(6));
    }

    private Notification notificationAt(String timestamp) {
        Notification notification = new Notification();
        notification.setCreatedAt(LocalDateTime.parse(timestamp));
        return notification;
    }
}
