/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package com.alibaba.fastjson2.benchmark.eishay;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.benchmark.eishay.vo.MediaContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nop.commons.util.IoHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class EishayParseStringPretty {
    static String str;
    static ObjectMapper mapper = new ObjectMapper();

    static {
        try {
            InputStream is = EishayParseStringPretty.class.getClassLoader().getResourceAsStream("data/eishay.json");
            str = IoHelper.readText(is, "UTF-8");
        } catch (Throwable ex) {
            ex.printStackTrace();
        }

        CoreInitialization.initialize();
    }

    // @Benchmark
    public void fastjson1(Blackhole bh) {
        bh.consume(com.alibaba.fastjson.JSON.parseObject(str, MediaContent.class));
    }

    // @Benchmark
    public void fastjson2(Blackhole bh) {
        bh.consume(JSON.parseObject(str, MediaContent.class));
    }

    // @Benchmark
    public void jackson(Blackhole bh) throws Exception {
        bh.consume(mapper.readValue(str, MediaContent.class));
    }

    @Benchmark
    public void xlang(Blackhole bh) {
        bh.consume(JsonTool.parseBeanFromText(str, MediaContent.class));
    }

    // @Test
    public void fastjson2_perf_test() {
        for (int i = 0; i < 10; i++) {
            fastjson2_perf();
        }
    }

    public static void fastjson2_perf() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 1000; ++i) {
            JSON.parseObject(str, MediaContent.class);
        }
        long millis = System.currentTimeMillis() - start;
        System.out.println("millis : " + millis);
        // zulu17.32.13 : 663 761 757 649
        // zulu11.52.13 : 567 551
        // zulu8.58.0.13 : 649 624 638
    }

    public static void main(String[] args) throws RunnerException {
        // for (long i = 0; i < 10000000000L; i++) {
        // new EishayParseStringPretty().xlang(new Blackhole("Today's password is swordfish. I understand instantiating
        // Blackholes directly is dangerous."));
        // }

        // new EishayParseStringPretty().fastjson2_perf_test();
        Options options = new OptionsBuilder().include(EishayParseStringPretty.class.getName()).mode(Mode.Throughput)
                .timeUnit(TimeUnit.MILLISECONDS).forks(1).build();
        new Runner(options).run();
    }
}
/*
 * Benchmark Mode Cnt Score Error Units EishayParseStringPretty.fastjson1 thrpt 5 261.772 ± 84.624 ops/ms
 * EishayParseStringPretty.fastjson2 thrpt 5 959.999 ± 211.260 ops/ms EishayParseStringPretty.jackson thrpt 5 458.239 ±
 * 42.683 ops/ms EishayParseStringPretty.xlang thrpt 5 91.725 ± 27.840 ops/ms
 */