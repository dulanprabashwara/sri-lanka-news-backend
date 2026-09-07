package lk.srilankannews.retention;

import java.util.Objects;

public final class RedisStreamID implements Comparable<RedisStreamID> {
    public static final RedisStreamID ZERO = new RedisStreamID(0L, 0L);

    private final long timestampMs;
    private final long sequence;
    private final String rawId;

    public RedisStreamID(long timestampMs, long sequence) {
        this.timestampMs = timestampMs;
        this.sequence = sequence;
        this.rawId = timestampMs + "-" + sequence;
    }

    public static RedisStreamID parse(String id) {
        if (id == null || id.isBlank()) {
            return ZERO;
        }
        int dashIndex = id.indexOf('-');
        if (dashIndex <= 0) {
            try {
                long ts = Long.parseLong(id.trim());
                return new RedisStreamID(ts, 0L);
            } catch (NumberFormatException e) {
                return ZERO;
            }
        }
        try {
            long ts = Long.parseLong(id.substring(0, dashIndex).trim());
            long seq = Long.parseLong(id.substring(dashIndex + 1).trim());
            return new RedisStreamID(ts, seq);
        } catch (NumberFormatException e) {
            return ZERO;
        }
    }

    public static RedisStreamID fromEpochMilli(long epochMilli) {
        return new RedisStreamID(Math.max(0L, epochMilli), 0L);
    }

    public long timestampMs() {
        return timestampMs;
    }

    public long sequence() {
        return sequence;
    }

    public String rawId() {
        return rawId;
    }

    public boolean isZero() {
        return timestampMs == 0L && sequence == 0L;
    }

    @Override
    public int compareTo(RedisStreamID o) {
        if (o == null) {
            return 1;
        }
        int cmp = Long.compare(this.timestampMs, o.timestampMs);
        if (cmp != 0) {
            return cmp;
        }
        return Long.compare(this.sequence, o.sequence);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RedisStreamID that = (RedisStreamID) o;
        return timestampMs == that.timestampMs && sequence == that.sequence;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestampMs, sequence);
    }

    @Override
    public String toString() {
        return rawId;
    }
}
