package io.nop.biz.crud;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;

public class BizFilterNodeGenerator implements IXNodeGenerator {
    private final XNode node;

    public BizFilterNodeGenerator(XNode node) {
        this.node = node;
    }

    @Override
    public XNode generateNode(IEvalContext context) {
        return node.cloneInstance();
    }
}
