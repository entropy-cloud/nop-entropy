package io.nop.ofbiz.transform;

import io.nop.core.lang.xml.XNode;

public class EntityRefModelToOrmModel {
    public XNode transform(XNode node) {
        XNode ret = XNode.make("orm");
        ret.setAttr("x:schema", "/nop/schema/orm/orm.xdef");
        ret.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");

        
        return ret;
    }
}
