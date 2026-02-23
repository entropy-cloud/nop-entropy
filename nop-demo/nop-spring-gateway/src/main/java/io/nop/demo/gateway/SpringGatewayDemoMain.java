/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.demo.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Gateway Demo Application
 * <p>
 * 演示如何在Spring Boot中使用nop-gateway和nop-tcc-integration，
 * 实现通过网关的请求自动开启TCC分布式事务。
 */
@SpringBootApplication
public class SpringGatewayDemoMain {

    public static void main(String[] args) {
        SpringApplication.run(SpringGatewayDemoMain.class, args);
    }
}
