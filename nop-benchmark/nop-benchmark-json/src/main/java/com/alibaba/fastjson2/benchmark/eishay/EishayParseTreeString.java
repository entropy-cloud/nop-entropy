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
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class EishayParseTreeString {
    static String str;
    static ObjectMapper mapper = new ObjectMapper();

    static {
        try {
            InputStream is = EishayParseTreeString.class.getClassLoader()
                    .getResourceAsStream("data/eishay_compact.json");
            str = IoHelper.readText(is, "UTF-8");
            JSON.parseObject(str, MediaContent.class);
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
        CoreInitialization.initialize();
    }

    @Benchmark
    public void fastjson1(Blackhole bh) {
        bh.consume(com.alibaba.fastjson.JSON.parseObject(str));
    }

    @Benchmark
    public void fastjson2(Blackhole bh) {
        bh.consume(JSON.parseObject(str));
    }

    @Benchmark
    public void jackson(Blackhole bh) throws Exception {
        bh.consume(mapper.readValue(str, HashMap.class));
    }

    @Benchmark
    public void xlang(Blackhole bh) {
        bh.consume(JsonTool.instance().parseFromText(null, str, null));
    }

    // @Test
    public void fastjson1_perf_test() {
        for (int i = 0; i < 10; i++) {
            fastjson1_perf();
        }
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
            JSON.parseObject(str);
        }
        long millis = System.currentTimeMillis() - start;
        System.out.println("millis : " + millis);
        // zulu17.32.13 : 769 777 760
        // zulu11.52.13 : 796 900 891
        // zulu8.58.0.13 : 720 745 750 713
    }

    public static void fastjson1_perf() {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000 * 1000; ++i) {
            com.alibaba.fastjson.JSON.parseObject(str);
        }
        long millis = System.currentTimeMillis() - start;
        System.out.println("millis : " + millis);
        // zulu17.32.13 :
        // zulu11.52.13 : 928
        // zulu8.58.0.13 :
    }

    public static void main(String[] args) throws RunnerException {
        // new EishayParseTreeString().fastjson2_perf_test();
        Options options = new OptionsBuilder().include(EishayParseTreeString.class.getName()).mode(Mode.Throughput)
                .timeUnit(TimeUnit.MILLISECONDS).forks(1).build();
        new Runner(options).run();
    }
}
/*
 *
 * Benchmark Mode Cnt Score Error Units EishayParseTreeString.fastjson1 thrpt 5 438.545 ± 152.411 ops/ms
 * EishayParseTreeString.fastjson2 thrpt 5 731.222 ± 380.592 ops/ms EishayParseTreeString.jackson thrpt 5 472.267 ±
 * 290.477 ops/ms EishayParseTreeString.xlang thrpt 5 209.798 ± 52.287 ops/ms EishayParseTreeStringPretty.fastjson1
 * thrpt 5 244.739 ± 52.473 ops/ms EishayParseTreeStringPretty.fastjson2 thrpt 5 461.589 ± 498.441 ops/ms
 * EishayParseTreeStringPretty.jackson thrpt 5 386.229 ± 335.222 ops/ms EishayParseTreeStringPretty.xlang thrpt 5
 * 193.004 ± 21.857 ops/ms
 */