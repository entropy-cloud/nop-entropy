package io.nop.xui.initialize;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.domain.SimpleStdDomainHandlers;
import io.nop.xui.vue.VueNode;
import io.nop.xui.vue.VueTemplateParser;

public class VueNodeStdDomainHandler extends SimpleStdDomainHandlers.XmlType {
    public static final VueNodeStdDomainHandler INSTANCE = new VueNodeStdDomainHandler();

    @Override
    public IGenericType getGenericType(boolean mandatory, String options) {
        return ReflectionManager.instance().buildRawType(VueNode.class);
    }

    @Override
    public String getName() {
        return XDefConstants.STD_DOMAIN_VUE_NODE;
    }

    @Override
    public Object parseProp(String options, SourceLocation loc, String propName, Object text, XLangCompileTool cp) {
        return null;
    }

    @Override
    public Object parseXmlChild(String options, XNode body, XLangCompileTool cp) {
        return new VueTemplateParser().parseTemplate(body);
    }
}
