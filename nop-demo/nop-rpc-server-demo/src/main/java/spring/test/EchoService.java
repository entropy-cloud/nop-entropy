/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package spring.test;

import io.nop.api.core.beans.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import spring.test.feign.FeignTestRpc;
import spring.test.feign.MyRequest;
import spring.test.feign.MyResponse;

import java.util.Map;

@RestController
public class EchoService {
    @PostMapping(value = "/echo/{id}")
    public String echo(@RequestParam("msg") String msg, @PathVariable("id") String id) {
        return "Hello Nacos Discovery " + msg + ",id=" + id;
    }

    @PostMapping("/r/Demo__hello")
    public ApiResponse<?> hello(@RequestBody Map<String,Object> body) {
        return ApiResponse.buildSuccess("result:" + body);
    }

    @Autowired
    FeignTestRpc testRpc;

    @GetMapping("/test_rpc")
    public MyResponse testRpc(@RequestParam("name") String name) {
        MyRequest request = new MyRequest();
        request.setName(name);

        return testRpc.myMethod(request, "value1").get();
    }
}