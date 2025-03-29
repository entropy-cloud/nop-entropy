package io.nop.tcc.core.meta;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.annotations.txn.TccMethod;
import io.nop.api.core.annotations.txn.TccTransactional;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.ReflectionManager;

import java.util.HashMap;
import java.util.Map;

public class ReflectionTccServiceMetaBuilder {
    public static ReflectionTccServiceMetaBuilder INSTANCE = new ReflectionTccServiceMetaBuilder();

    public TccServiceMeta build(String serviceName, Class<?> serviceClass) {
        IClassModel classModel = ReflectionManager.instance().getClassModel(serviceClass);
        Map<String, TccMethodMeta> methods = new HashMap<>();

        BizObjName bizObjNameAnn = classModel.getAnnotation(BizObjName.class);
        String bizObjName = bizObjNameAnn == null ? classModel.getSimpleName() : bizObjNameAnn.value();

        classModel.getMethods().forEach(method -> {
            TccMethod tccMethod = method.getAnnotation(TccMethod.class);
            TccTransactional txn = method.getAnnotation(TccTransactional.class);
            if (tccMethod != null) {
                String confirmMethod = tccMethod.confirmMethod();
                String cancelMethod = tccMethod.cancelMethod();
                String methodName = method.getName();
                if (method.isAsync() && methodName.endsWith("Async"))
                    methodName = StringHelper.removeEnd(methodName, "Async");

                if (confirmMethod != null)
                    confirmMethod = bizObjName + "__" + confirmMethod;
                cancelMethod = bizObjName + "__" + cancelMethod;
                methodName = bizObjName + "__" + methodName;
                String txnGroup = txn == null ? null : txn.txnGroup();
                methods.put(methodName, new TccMethodMeta(txnGroup, methodName, confirmMethod, cancelMethod));
            }
        });

        return new TccServiceMeta(serviceName, methods);
    }
}
