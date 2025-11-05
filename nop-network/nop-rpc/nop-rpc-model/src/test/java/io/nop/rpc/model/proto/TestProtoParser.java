/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model.proto;

import io.nop.core.unittest.BaseTestCase;
import io.nop.rpc.model.ApiModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestProtoParser extends BaseTestCase {
    @Test
    public void testParse() {
        String protoText = attachmentText("test1.proto");
        ApiModel model = new ProtoFileParser().parseFromText(null, protoText);
        String protoText2 = new ProtoFileGenerator().generateProtoFile(model);
        System.out.println(protoText2);
        assertEquals(normalizeCRLF(protoText.trim()), normalizeCRLF(protoText2.trim()));
    }
}
