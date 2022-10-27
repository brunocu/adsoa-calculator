package distributed.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

final public class UID {
    private UID() {
    }

    private static long getTimeForUIDNanos() {
        LocalDateTime start = LocalDateTime.of(1582, 10, 15, 0, 0, 0);
        Duration duration = Duration.between(start, LocalDateTime.now());
        long seconds = duration.getSeconds();
        long nanos = duration.getNano();
        return (seconds * 10000000 + nanos * 100) >> 4;
    }

    /**
     * Generates a 64-bit Unique ID
     * <p>
     * Based on Version 1 UUID (RFC 4122, Algorithms for Creating a Time-Based UUID)
     */
    public static long generateUID() {
        // 48 MSB
        long id = getTimeForUIDNanos() << (64 - 48);
        // Random 16 MSB
        Random random = new Random();
        id |= random.nextLong() & 0xFFFFL;
        return id;
    }
}
