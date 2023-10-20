/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package spring.test;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@RestController
public class EchoService {
    @PostMapping(value = "/echo/{id}")
    public String echo(@QueryParam("msg") String msg, @PathParam("id") String id) {
        return "Hello Nacos Discovery " + msg + ",id=" + id;
    }
}