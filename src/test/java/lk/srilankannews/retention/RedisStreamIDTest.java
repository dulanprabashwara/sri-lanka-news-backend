package lk.srilankannews.retention;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedisStreamIDTest {

    @Test
    void verifiesNumericStreamIdOrdering() {
        RedisStreamID id1 = RedisStreamID.parse("100-0");
        RedisStreamID id2 = RedisStreamID.parse("100-1");
        RedisStreamID id3 = RedisStreamID.parse("101-0");
        RedisStreamID id4 = RedisStreamID.parse("999-0");
        RedisStreamID id5 = RedisStreamID.parse("1000-0");

        assertThat(id1).isLessThan(id2);
        assertThat(id2).isLessThan(id3);
        assertThat(id3).isLessThan(id4);
        assertThat(id4).isLessThan(id5);
    }

    @Test
    void parsesInvalidAndNullIdsAsZero() {
        assertThat(RedisStreamID.parse(null).isZero()).isTrue();
        assertThat(RedisStreamID.parse("").isZero()).isTrue();
        assertThat(RedisStreamID.parse("invalid").isZero()).isTrue();
        assertThat(RedisStreamID.parse("0-0").isZero()).isTrue();
    }

    @Test
    void createsFromEpochMilli() {
        RedisStreamID id = RedisStreamID.fromEpochMilli(1680000000000L);
        assertThat(id.rawId()).isEqualTo("1680000000000-0");
        assertThat(id.timestampMs()).isEqualTo(1680000000000L);
        assertThat(id.sequence()).isEqualTo(0L);
    }
}
