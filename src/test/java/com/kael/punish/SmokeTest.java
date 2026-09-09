package com.kael.punish;

import com.kael.punish.util.DurationParser;
import com.kael.punish.util.IpFilter;
import com.kael.punish.util.Messages;
import com.kael.punish.util.OfflineUuid;

public final class SmokeTest {

    private SmokeTest() {
    }

    public static void main(String[] args) {
        assertEquals(30L * 60_000L, DurationParser.parse("30m"));
        assertEquals(12L * 60L * 60_000L, DurationParser.parse("12h"));
        assertEquals(7L * 24L * 60L * 60_000L, DurationParser.parse("7d"));
        assertEquals(365L * 24L * 60L * 60_000L, DurationParser.parse("1y"));
        assertThrows(() -> DurationParser.parse("abc"));
        assertThrows(() -> DurationParser.parse("0m"));
        assertEquals("0.0 小时", DurationParser.formatRemaining(1L));
        assertEquals("0.5 小时", DurationParser.formatRemaining(30L * 60_000L));
        assertEquals("1 天 0.0 小时", DurationParser.formatRemaining(24L * 60L * 60_000L));
        assertEquals("3.6 小时", DurationParser.formatRemaining(3_600_000L * 36 / 10));
        assertEquals("1 天 3.6 小时", DurationParser.formatRemaining(3_600_000L * 276 / 10));

        assertEquals("127.0.0.1", IpFilter.normalize("::ffff:127.0.0.1"));
        assertTrue(IpFilter.isPublic("8.8.8.8"));
        assertFalse(IpFilter.isPublic("127.0.0.1"));
        assertFalse(IpFilter.isPublic("192.168.1.1"));

        String uuid = OfflineUuid.fromName("Steve").toString();
        assertEquals("5627dd98-e6be-3c21-b8a8-e92344183641", uuid);
        if (uuid.length() != 36) {
            throw new IllegalStateException("离线 UUID 格式错误: " + uuid);
        }
        if (Messages.banScreen(newBan()).toString().isBlank()) {
            throw new IllegalStateException("中文封禁界面为空");
        }

        System.out.println("SmokeTest OK, Steve offline uuid = " + uuid);
    }

    private static com.kael.punish.storage.BanRecord newBan() {
        return new com.kael.punish.storage.BanRecord(
                1L,
                "KB-TEST1234",
                com.kael.punish.storage.BanRecord.Type.BAN,
                OfflineUuid.fromName("Steve").toString(),
                "Steve",
                "steve",
                null,
                "测试原因",
                "控制台",
                System.currentTimeMillis(),
                null,
                true
        );
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new IllegalStateException("期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("期望 " + expected + "，实际 " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new IllegalStateException("期望为 true");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new IllegalStateException("期望为 false");
        }
    }

    private static void assertThrows(Runnable runnable) {
        try {
            runnable.run();
            throw new IllegalStateException("期望抛出异常");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
