package io.nop.biz.model;

import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xmeta.ISchema;

public interface ITypeDeclaration extends IPropGetMissingHook {
    IGenericType getType();

    ISchema getSchema();

    boolean isMandatory();
}
