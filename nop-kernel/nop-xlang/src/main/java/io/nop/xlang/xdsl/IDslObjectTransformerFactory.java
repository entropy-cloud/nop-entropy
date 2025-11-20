package io.nop.xlang.xdsl;

public interface IDslObjectTransformerFactory {
    interface IDslObjectTransformer {
        Object transform(Object obj);
    }

    IDslObjectTransformer newTransformer(String mappingName);
}
