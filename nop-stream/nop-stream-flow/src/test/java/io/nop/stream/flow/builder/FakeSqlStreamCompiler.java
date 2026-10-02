/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder;

import java.util.Map;

/**
 * WI17 B5 layer-1 test fixture: a fake {@code ISqlStreamCompiler} that ignores the SQL
 * text and returns a fixed two-node model XML. Its product's transform/edge ids let the
 * expansion test prove the parent model content was replaced by the SPI product.
 */
public class FakeSqlStreamCompiler implements io.nop.stream.flow.spi.ISqlStreamCompiler {

    @Override
    public String compileSql(String sql, Map<String, String> schema, String sinkBean) {
        return "<stream x:schema=\"/nop/schema/stream/stream.xdef\" "
                + "xmlns:x=\"/nop/schema/xdsl.xdef\" name=\"fake-compiled\" version=\"1\">"
                + "<transforms>"
                + "<source id=\"fakeSrc\" bean=\"fakeSrcFn\"/>"
                + "<map id=\"fakeMap\"><source>return event;</source></map>"
                + "<sink id=\"out\" bean=\"" + sinkBean + "\"/>"
                + "</transforms>"
                + "<edges>"
                + "<edge id=\"fake-e0\" from=\"fakeSrc\" to=\"fakeMap\"/>"
                + "<edge id=\"fake-e1\" from=\"fakeMap\" to=\"out\"/>"
                + "</edges>"
                + "</stream>";
    }
}
