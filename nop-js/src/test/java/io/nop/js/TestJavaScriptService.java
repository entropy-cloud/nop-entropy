/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.js;

import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.unittest.BaseTestCase;
import io.nop.js.engine.JavaScriptService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestJavaScriptService extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    JavaScriptService newService() {
        JavaScriptService service = new JavaScriptService();
        service.setInitScriptPath("/nop/js/test-server.mjs");
        service.setJsLibLoader(path -> {
            IResource resource = VirtualFileSystem.instance().getResource(path);
            return ResourceHelper.readText(resource);
        });
        return service;
    }

    @Test
    public void testExecute() {
        forceStackTrace();
        JavaScriptService service = newService();
        service.start();
        CompletionStage<Object> future = service.invokeAsync("rollupTransform", "/localhost/a.mjs",
                "import bModule from './b.mjs'; const x = 1; \n" +
                        "\t\texport function h(){\n" +
                        "\t\t\treturn x;\n" +
                        "\t\t}\n" +
                        "\t\texport default {a:bModule};");
        String result = (String) FutureHelper.syncGet(future);
        System.out.println(result);
        service.stop();
    }

    @Test
    public void testReturnMap() {
        JavaScriptService service = newService();
        service.start();
        CompletionStage<Object> future = service.invokeAsync("test_map", "a", "b");
        Map<String, Object> result = (Map<String, Object>) FutureHelper.syncGet(future);
        assertEquals("{a: 1, b: {c: [1, 2, \"a\"]}}", result.toString());
        System.out.println(result);
        service.stop();
    }

    @Test
    public void testSpeed() {
        JavaScriptService service = newService();
        service.start();
        for (int i = 0; i < 10; i++) {
            CompletionStage<Object> future = service.invokeAsync("rollupTransform", "/localhost/a.mjs",
                    "import bModule from './b.mjs'; const x = 1; \n" +
                            "\t\texport function h(){\n" +
                            "\t\t\treturn x;\n" +
                            "\t\t}\n" +
                            "\t\texport default {a:bModule};");
            FutureHelper.syncGet(future);
        }
        service.stop();
    }

    @Test
    public void testRollup() {
        JavaScriptService service = newService();
        service.start();

        String source = ResourceHelper.readText(VirtualFileSystem.instance().getResource("/nop/js/test.lib.js"));
        CompletionStage<Object> future = service.invokeAsync("rollupTransform", "/nop/js/test.lib.js", source);
        String result = (String) FutureHelper.syncGet(future);
        System.out.println(result);
        assertEquals(normalizeCRLF(attachmentText("rollup-test.lib.js")),
                normalizeCRLF(result));

        service.stop();
    }

    @Test
    public void testImportLib() {
        JavaScriptService service = newService();
        service.start();

        String source = ResourceHelper.readText(VirtualFileSystem.instance().getResource("/nop/js/test-demo.lib.xjs"));
        CompletionStage<Object> future = service.invokeAsync("rollupTransform", "/nop/js/test-demo.lib.js", source);
        String result = (String) FutureHelper.syncGet(future);
        System.out.println(result);
        assertEquals(normalizeCRLF(attachmentText("test-demo-gen.lib.js")),
                normalizeCRLF(result));

        service.stop();
    }
}
