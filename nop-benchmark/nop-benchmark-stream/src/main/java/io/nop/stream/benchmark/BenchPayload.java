/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.benchmark;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Benchmark payload POJO: five scalar fields plus one nested object, ~200B serialized.
 *
 * <p>This class deliberately lives under the {@code io.nop.stream.} package prefix:
 * {@code StreamElementCodec.decode} validates the envelope's {@code valueType} through
 * {@code ClassNameValidator}, which only allows class names starting with
 * {@code io.nop.stream.} (among a few platform prefixes). A payload class placed in the
 * benchmark module's own {@code io.nop.benchmark.stream} package would fail that
 * whitelist check at decode time.
 *
 * <p>{@code @DataBean} is mandatory: {@code JsonTool.stringify} only accepts beans
 * carrying this marker annotation.
 */
@DataBean
public class BenchPayload {

    private String deviceId;
    private long sequence;
    private double temperature;
    private boolean active;
    private InnerMetric metric;

    public BenchPayload() {
    }

    public BenchPayload(String deviceId, long sequence, double temperature, boolean active,
                        InnerMetric metric) {
        this.deviceId = deviceId;
        this.sequence = sequence;
        this.temperature = temperature;
        this.active = active;
        this.metric = metric;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public InnerMetric getMetric() {
        return metric;
    }

    public void setMetric(InnerMetric metric) {
        this.metric = metric;
    }

    /** Nested object carried by every benchmark payload. */
    @DataBean
    public static class InnerMetric {

        private String name;
        private long value;

        public InnerMetric() {
        }

        public InnerMetric(String name, long value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getValue() {
            return value;
        }

        public void setValue(long value) {
            this.value = value;
        }
    }
}
