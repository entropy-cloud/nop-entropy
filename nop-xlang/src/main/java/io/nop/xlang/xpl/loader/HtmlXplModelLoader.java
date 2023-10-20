/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.loader;

import io.nop.xlang.ast.XLangOutputMode;

public class HtmlXplModelLoader extends XplModelLoader {
    public HtmlXplModelLoader() {
        super(XLangOutputMode.html);
    }
}
