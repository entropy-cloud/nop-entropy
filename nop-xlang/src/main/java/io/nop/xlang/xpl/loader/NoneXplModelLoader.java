package io.nop.xlang.xpl.loader;

import io.nop.xlang.ast.XLangOutputMode;

public class NoneXplModelLoader extends XplModelLoader {
    public NoneXplModelLoader() {
        super(XLangOutputMode.none);
    }
}
