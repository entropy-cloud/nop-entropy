package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;

public interface IDslNodeTextSerializer {
    String serializeDslNodeToText(String fileType, XNode node);
}
