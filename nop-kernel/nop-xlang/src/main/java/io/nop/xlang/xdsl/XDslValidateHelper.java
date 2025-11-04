package io.nop.xlang.xdsl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ClassHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;

import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDSL_ATTR_NOT_BEAN_PROP;

public class XDslValidateHelper {
    public static void validateForClassProps(XNode node, Class<?> clazz) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);

        for (String attrName : node.getAttrNames()) {
            IBeanPropertyModel propModel = beanModel.getPropertyModel(attrName);
            if (propModel == null || propModel.getSetter() == null)
                throw new NopException(ERR_XDSL_ATTR_NOT_BEAN_PROP)
                        .loc(node.attrLoc(attrName))
                        .param(ARG_ATTR_NAME, attrName).param(ARG_CLASS_NAME, ClassHelper.getCanonicalClassName(clazz));
        }
    }
}