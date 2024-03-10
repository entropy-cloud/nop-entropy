/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package org.beetl.sql.jmh;

import org.beetl.sql.jmh.beetl.BeetlSQLService;
import org.beetl.sql.jmh.jdbc.JdbcService;
import org.beetl.sql.jmh.jpa.SpringBoot;
import org.beetl.sql.jmh.jpa.SpringService;
import org.beetl.sql.jmh.mybatis.MyBatisSpringBoot;
import org.beetl.sql.jmh.mybatis.MyBatisSpringService;
import org.beetl.sql.jmh.weed.WeedService;
import org.beetl.sql.jmh.xorm.NopBoot;
import org.beetl.sql.jmh.xorm.NopOrmService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 性能测试入口,数据是Throughput，越大越好
 */
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JMHMain {
    JdbcService jdbcService = null;
    BeetlSQLService beetlSQLService = null;
    SpringService springService = null;
    MyBatisSpringService myBatisSpringService = null;
    WeedService weedService = null;

    NopOrmService nopService = null;

    @Setup
    public void init() {
        NopBoot nopBoot = new NopBoot();
        nopBoot.init();
        nopService = nopBoot.getService();

        jdbcService = new JdbcService();
        jdbcService.init();

        beetlSQLService = new BeetlSQLService();
        beetlSQLService.init();

        SpringBoot springBoot = new SpringBoot();
        springBoot.init();
        springService = springBoot.getService();

        MyBatisSpringBoot myBatisSpringBoot = new MyBatisSpringBoot();
        myBatisSpringBoot.init();
        myBatisSpringService = myBatisSpringBoot.getService();

        // weedService = new WeedService();
        // weedService.init();

    }

    /* JDBC,基准，有些方法性能飞快 */
    // @Benchmark
    // public void jdbcInsert() {
    // jdbcService.addEntity();
    // }
    //
    // @Benchmark
    // public void jdbcSelectById() {
    // jdbcService.getEntity();
    // }
    //
    // @Benchmark
    // public void jdbcExecuteJdbc() {
    // jdbcService.executeJdbcSql();
    // }
    //
    //
    // /* mybatis */
    // @Benchmark
    // public void mybatisInsert() {
    // myBatisSpringService.addEntity();
    // }
    //
    // @Benchmark
    // public void mybatisSelectById() {
    // myBatisSpringService.getEntity();
    // }
    //
    // @Benchmark
    // public void mybatisLambdaQuery() {
    // myBatisSpringService.lambdaQuery();
    // }
    //
    // @Benchmark
    // public void mybatisExecuteTemplate() {
    // myBatisSpringService.executeTemplateSql();
    // }
    //
    // @Benchmark
    // public void mybatisFile() {
    // myBatisSpringService.sqlFile();
    // }
    //
    // @Benchmark
    // public void mybatisPageQuery() {
    // myBatisSpringService.pageQuery();
    // }
    //
    // @Benchmark
    // public void mybatisComplexMapping() {
    // myBatisSpringService.complexMapping();
    // }

    /* BeetlSQL */
    // @Benchmark
    // public void beetlsqlInsert() {
    // beetlSQLService.addEntity();
    // }
    //
    // @Benchmark
    // public void beetlsqlSelectById() {
    // beetlSQLService.getEntity();
    // }
    //
    // @Benchmark
    // public void beetlsqlLambdaQuery() {
    // beetlSQLService.lambdaQuery();
    // }
    //
    // @Benchmark
    // public void beetlsqlExecuteJdbc() {
    // beetlSQLService.executeJdbcSql();
    // }
    //
    // @Benchmark
    // public void beetlsqlExecuteTemplate() {
    // beetlSQLService.executeTemplateSql();
    // }
    //
    // @Benchmark
    // public void beetlsqlFile() {
    // beetlSQLService.sqlFile();
    // }
    //
    // @Benchmark
    // public void beetlsqlPageQuery() {
    // beetlSQLService.pageQuery();
    // }

    //
    // @Benchmark
    // public void beetlsqlOne2Many() {
    // beetlSQLService.one2Many();
    // }
    //
    @Benchmark
    public void beetlsqlComplexMapping() {
        beetlSQLService.complexMapping();
    }
    //
    // /* BeetlSQL */
    // @Benchmark
    // public void nopInsert() {
    // nopService.addEntity();
    // }
    //
    // @Benchmark
    // public void nopSelectById() {
    // nopService.getEntity();
    // }
    //
    // @Benchmark
    // public void nopLambdaQuery() {
    // nopService.lambdaQuery();
    // }
    //
    // @Benchmark
    // public void nopExecuteJdbc() {
    // nopService.executeJdbcSql();
    // }
    //
    // @Benchmark
    // public void nopExecuteTemplate() {
    // nopService.executeTemplateSql();
    // }
    //
    // @Benchmark
    // public void nopSqlFile() {
    // nopService.sqlFile();
    // }

    // @Benchmark
    // public void nopPageQuery() {
    // nopService.pageQuery();
    // }
    //

    // @Benchmark
    // public void nopOne2Many() {
    // nopService.one2Many();
    // }
    //
    @Benchmark
    public void nopComplexMapping() {
        nopService.complexMapping();
    }
    //
    //
    // // /* Spring Data JPA */
    // @Benchmark
    // public void jpaInsert() {
    // springService.addEntity();
    // }
    //
    // @Benchmark
    // public void jpaSelectById() {
    // springService.getEntity();
    // }
    //
    // @Benchmark
    // public void jpaExecuteJdbc() {
    // springService.executeJdbcSql();
    // }
    //
    // /*实际上JPA并不支持template，但勉强用HQl来测试*/
    // @Benchmark
    // public void jpaExecuteTemplate() {
    // springService.executeTemplateSql();
    // }
    //
    // @Benchmark
    // public void jpaOne2Many() {
    // springService.one2Many();
    // }

    // @Benchmark
    // public void jpaPageQuery() {
    // springService.pageQuery();
    // }

    // /* Weed3 */
    // @Benchmark
    // public void weedInsert() {
    // weedService.addEntity();
    // }
    //
    // @Benchmark
    // public void weedSelectById() {
    // weedService.getEntity();
    // }
    //
    // @Benchmark
    // public void weedLambdaQuery() {
    // weedService.lambdaQuery();
    // }
    //
    // @Benchmark
    // public void weedExecuteJdbc() {
    // weedService.executeJdbcSql();
    // }
    //
    // @Benchmark
    // public void weedExecuteTemplate() {
    // weedService.executeTemplateSql();
    // }
    //
    // @Benchmark
    // public void weedFile() {
    // weedService.sqlFile();
    // }
    //
    // @Benchmark
    // public void weedPageQuery() {
    // weedService.pageQuery();
    // }

    public static void main(String[] args) throws RunnerException {
        // test();
        Options opt = new OptionsBuilder().include(JMHMain.class.getSimpleName()).build();
        new Runner(opt).run();
    }

    /**
     * 先单独运行一下保证每个测试都没有错误
     */
    public static void test() {
        JMHMain jmhMain = new JMHMain();
        jmhMain.init();
        for (int i = 0; i < 3; i++) {
            Method[] methods = jmhMain.getClass().getMethods();
            for (Method method : methods) {
                if (method.getAnnotation(Benchmark.class) == null) {
                    continue;
                }
                try {

                    method.invoke(jmhMain, new Object[0]);

                } catch (Exception ex) {
                    throw new IllegalStateException(" method " + method.getName(), ex);
                }

            }
        }

    }

}
