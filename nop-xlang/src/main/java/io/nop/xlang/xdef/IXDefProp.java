package io.nop.xlang.xdef;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.core.reflect.hook.IPropGetMissingHook;

public interface IXDefProp extends IPropGetMissingHook, ISourceLocationGetter {
    String getName();
}
