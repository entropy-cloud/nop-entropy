/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.metrics;

// copy from LoggingMeterRegistry

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static io.micrometer.core.instrument.util.DoubleFormat.decimalOrNan;
import static java.util.stream.Collectors.joining;

/**
 * 输出MeterRegistry中的指标到loggingSink中。 将LoggingMeterRegistry中的打印功能剥离到独立的实现中，可以用于输出任意MeterRegistry中的信息。
 */
public class MeterPrinter {
    private final MeterRegistry meterRegistry;
    private final MeterPrintConfig config;
    private final Function<Meter, String> meterIdPrinter;

    public MeterPrinter(MeterRegistry meterRegistry, MeterPrintConfig config,
                        @Nullable Function<Meter, String> meterIdPrinter) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.meterIdPrinter = meterIdPrinter != null ? meterIdPrinter : defaultMeterIdPrinter();
    }

    public static String scrape(MeterRegistry registry, MeterPrintConfig config) {
        StringBuilder sb = new StringBuilder();
        new MeterPrinter(registry, config, null).print(s -> sb.append(s).append('\n'));
        return sb.toString();
    }

    private Function<Meter, String> defaultMeterIdPrinter() {
        return (meter) -> getConventionName(meter.getId()) + getConventionTags(meter.getId()).stream()
                .map(t -> t.getKey() + "=" + t.getValue()).collect(joining(",", "{", "}"));
    }

    protected String getConventionName(Meter.Id id) {
        return id.getConventionName(config.namingConvention());
    }

    protected List<Tag> getConventionTags(Meter.Id id) {
        return id.getConventionTags(config.namingConvention());
    }

    public void print(Consumer<String> loggingSink) {
        meterRegistry.getMeters().stream().sorted((m1, m2) -> {
            int typeComp = m1.getId().getType().compareTo(m2.getId().getType());
            if (typeComp == 0) {
                return m1.getId().getName().compareTo(m2.getId().getName());
            }
            return typeComp;
        }).forEach(m -> {
            Printer print = new Printer(m);
            m.use(gauge -> loggingSink.accept(print.id() + " value=" + print.value(gauge.value())), counter -> {
                double count = counter.count();
                if (!config.logInactive() && count == 0)
                    return;
                loggingSink.accept(print.id() + " throughput=" + print.rate(count));
            }, timer -> {
                HistogramSnapshot snapshot = timer.takeSnapshot();
                long count = snapshot.count();
                if (!config.logInactive() && count == 0)
                    return;
                loggingSink.accept(print.id() + " throughput=" + print.unitlessRate(count) + " mean="
                        + print.time(snapshot.mean(getBaseTimeUnit())) + " max="
                        + print.time(snapshot.max(getBaseTimeUnit())));
            }, summary -> {
                HistogramSnapshot snapshot = summary.takeSnapshot();
                long count = snapshot.count();
                if (!config.logInactive() && count == 0)
                    return;
                loggingSink.accept(print.id() + " throughput=" + print.unitlessRate(count) + " mean="
                        + print.value(snapshot.mean()) + " max=" + print.value(snapshot.max()));
            }, longTaskTimer -> {
                int activeTasks = longTaskTimer.activeTasks();
                if (!config.logInactive() && activeTasks == 0)
                    return;
                loggingSink.accept(print.id() + " active=" + print.value(activeTasks) + " duration="
                        + print.time(longTaskTimer.duration(getBaseTimeUnit())));
            }, timeGauge -> {
                double value = timeGauge.value(getBaseTimeUnit());
                if (!config.logInactive() && value == 0)
                    return;
                loggingSink.accept(print.id() + " value=" + print.time(value));
            }, counter -> {
                double count = counter.count();
                // if (!config.logInactive() && count == 0) return;
                loggingSink.accept(print.id() + " throughput=" + print.rate(count));
            }, timer -> {
                double count = timer.count();
                // if (!config.logInactive() && count == 0) return;
                loggingSink.accept(print.id() + " throughput=" + print.rate(count) + " mean="
                        + print.time(timer.mean(getBaseTimeUnit())));
            }, meter -> loggingSink.accept(writeMeter(meter, print)));
        });
    }

    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    String writeMeter(Meter meter, Printer print) {
        return StreamSupport.stream(meter.measure().spliterator(), false).map(ms -> {
            String msLine = ms.getStatistic().getTagValueRepresentation() + "=";
            switch (ms.getStatistic()) {
                case TOTAL:
                case MAX:
                case VALUE:
                    return msLine + print.value(ms.getValue());
                case TOTAL_TIME:
                case DURATION:
                    return msLine + print.time(ms.getValue());
                case COUNT:
                    return "throughput=" + print.rate(ms.getValue());
                default:
                    return msLine + decimalOrNan(ms.getValue());
            }
        }).collect(joining(", ", print.id() + " ", ""));
    }

    class Printer {
        private final Meter meter;

        Printer(Meter meter) {
            this.meter = meter;
        }

        String id() {
            return meterIdPrinter.apply(meter);
        }

        String time(double time) {
            return TimeUtils
                    .format(Duration.ofNanos((long) TimeUtils.convert(time, getBaseTimeUnit(), TimeUnit.NANOSECONDS)));
        }

        String rate(double rate) {
            return humanReadableBaseUnit(rate / (double) config.step().getSeconds()) + "/s";
        }

        String unitlessRate(double rate) {
            return decimalOrNan(rate / (double) config.step().getSeconds()) + "/s";
        }

        String value(double value) {
            return humanReadableBaseUnit(value);
        }

        // see https://stackoverflow.com/a/3758880/510017
        String humanReadableByteCount(double bytes) {
            int unit = 1024;
            if (bytes < unit || Double.isNaN(bytes))
                return decimalOrNan(bytes) + " B";
            int exp = (int) (Math.log(bytes) / Math.log(unit));
            String pre = "KMGTPE".charAt(exp - 1) + "i";
            return decimalOrNan(bytes / Math.pow(unit, exp)) + " " + pre + "B";
        }

        String humanReadableBaseUnit(double value) {
            String baseUnit = meter.getId().getBaseUnit();
            if (BaseUnits.BYTES.equals(baseUnit)) {
                return humanReadableByteCount(value);
            }
            return decimalOrNan(value) + (baseUnit != null ? " " + baseUnit : "");
        }
    }
}