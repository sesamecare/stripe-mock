package com.sesame.oss.stripemock.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class MutableClock extends Clock {
    private final ZoneId zoneId;
    private volatile Instant instant;

    public MutableClock(ZoneId zoneId, Instant instant) {
        this.zoneId = zoneId;
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(zone, instant);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public void plusSeconds(int seconds) {
        setInstant(this.instant.plusSeconds(seconds));
    }
}
