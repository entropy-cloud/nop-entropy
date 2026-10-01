package io.nop.autotest.core.spike;

import io.nop.dao.seq.ISequenceGenerator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * M1.3 acceptance ① monotonic test sequence generator (nop-app-erp plan
 * 2026-10-01-2255-1). Bean-overridden onto {@code nopSequenceGenerator} via the
 * explicitly-loaded {@code m01-fixture-spike.beans.xml} (mechanism v4: boot-time
 * {@code nop.ioc.app-beans.files} wiring + allow-override), so that the
 * PersistEnvBuilder startup snapshot captures a deterministic monotonic source and
 * the M0.1 Decision ③ assertion style (ii) becomes decidable in tests.
 */
public class M13MonotonicSequenceGenerator implements ISequenceGenerator {
    private final AtomicLong counter = new AtomicLong(1);

    @Override
    public long generateLong(String seqName, boolean useDefault) {
        return counter.incrementAndGet();
    }

    @Override
    public String generateString(String seqName, boolean useDefault) {
        return String.valueOf(counter.incrementAndGet());
    }

    /** current counter head — next generated long value will be head+1 */
    public long head() {
        return counter.get();
    }
}
