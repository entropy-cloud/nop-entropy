/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.IResourceParser;

public interface IXNodeParser extends IResourceParser<XNode> {

    IXNodeParser shouldTraceDepends(boolean b);

    IXNodeParser defaultEncoding(String defaultEncoding);

    /**
     * 解析得到的标签名和属性名是否通过String.intern()函数调用来去除重复
     */
    IXNodeParser intern(boolean shouldIntern);

    IXNodeParser keepComment(boolean keepComment);

    IXNodeParser keepWhitespace(boolean keepWhitespace);

    IXNodeParser handler(IXNodeHandler handler);

    /**
     * 设置是否解析xml片段。xml片段不包含DOCTYPE, 可能具有多个根节点，对应xml节点的body部分。 对于fragments模式，parseFromResource返回的是一个虚拟父节点，它的子节点才是最终所有内容
     *
     * @return
     */
    IXNodeParser forFragments(boolean forFragments);

    /**
     * 是否允许:attr="value"和@attr=value这种属性
     *
     * @param forHtml
     * @return
     */
    IXNodeParser forHtml(boolean forHtml);

    @Override
    XNode parseFromResource(IResource resource, boolean ignoreUnknown);

    XNode parseFromText(SourceLocation loc, String text);

    XNode parseSingleNode(TextScanner sc);
}