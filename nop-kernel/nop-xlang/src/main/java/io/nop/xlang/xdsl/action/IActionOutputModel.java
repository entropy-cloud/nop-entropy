package io.nop.xlang.xdsl.action;

import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xmeta.ISchema;

import java.util.HashMap;

public interface IActionOutputModel extends IPropGetMissingHook {
    String getName();

    String getDisplayName();

    String getDescription();

    IGenericType getType();

    ISchema getSchema();

    default XNode getSchemaNode() {
        ISchema schema = getSchema();
        if (schema == null)
            return null;
        return schema.toNode(new HashMap<>());
    }
}
