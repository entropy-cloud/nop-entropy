package io.nop.core.reflect.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_TEXT;
import static io.nop.core.CoreErrors.ERR_REFLECT_BEAN_CLASS_NO_FACTORY_METHOD;
import static io.nop.core.CoreErrors.ERR_REFLECT_INVALID_VALUE_TEXT_FOR_BEAN;

public class BeanReflectHelper {
    public static <T> T getValueByFactoryMethod(Class<T> clazz, Object bean, String propName) {
        Object value = BeanTool.getProperty(bean, propName);
        if (StringHelper.isEmptyObject(value))
            return null;

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        IFunctionModel fn = beanModel.getFactoryMethod();
        if (fn == null) {
            throw new NopException(ERR_REFLECT_BEAN_CLASS_NO_FACTORY_METHOD)
                    .param(ARG_CLASS_NAME, clazz.getName());
        }
        T ret = (T) fn.call1(null, value, DisabledEvalScope.INSTANCE);
        if (ret == null)
            throw new NopException(ERR_REFLECT_INVALID_VALUE_TEXT_FOR_BEAN)
                    .param(ARG_CLASS_NAME, clazz.getName())
                    .param(ARG_TEXT, value);
        return ret;
    }
}
