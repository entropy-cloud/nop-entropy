package io.nop.biz.proxy;

import io.nop.api.core.annotations.aop.AopProxy;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.impl.BizObjectImpl;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.ReflectionHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.context.IExecutionContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.hook.IMethodMissingHook;
import io.nop.core.type.IGenericType;
import io.nop.graphql.core.reflection.ReflectionBizModelBuilder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.nop.graphql.core.GraphQLErrors.ARG_ARG_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_METHOD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_METHOD_PARAM_NO_REFLECTION_NAME_ANNOTATION;

public class BizProxyInvocationHandler implements InvocationHandler {
    private final BizObjectImpl bizObject;
    private final Map<String, Function<Object[], Object>> callers;

    public BizProxyInvocationHandler(BizObjectImpl bizObject, Map<String, Function<Object[], Object>> callers) {
        this.callers = callers;
        this.bizObject = bizObject;
    }

    public static Object makeProxy(BizObjectImpl bizObj) {
        List<Class<?>> interfaces = new ArrayList<>();
        interfaces.add(IMethodMissingHook.class);

        for (Object bean : bizObj.getBizModelBeans()) {
            for (Class<?> intf : ClassHelper.getInheritedInterfaces(bean.getClass())) {
                if (intf == AopProxy.class)
                    continue;
                if (!interfaces.contains(intf))
                    interfaces.add(intf);
            }
        }

        if (bizObj.getBizModel().getImplements() != null) {
            for (IGenericType type : bizObj.getBizModel().getImplements()) {
                if (!interfaces.contains(type.getRawClass()))
                    interfaces.add(type.getRawClass());
            }
        }

        Map<String, Function<Object[], Object>> callers = buildActionMap(interfaces, bizObj);
        BizProxyInvocationHandler handler = new BizProxyInvocationHandler(bizObj, callers);

        return Proxy.newProxyInstance(ClassHelper.getDefaultClassLoader(),
                interfaces.toArray(new Class[0]), handler);
    }

    static Map<String, Function<Object[], Object>> buildActionMap(List<Class<?>> interfaces, BizObjectImpl bizObj) {
        Map<String, Function<Object[], Object>> map = new HashMap<>();
        for (Class<?> clazz : interfaces) {
            if (clazz == IMethodMissingHook.class)
                continue;

            IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
            for (IFunctionModel method : classModel.getMethods()) {
                String methodName = ReflectionBizModelBuilder.INSTANCE.getServiceActionName(method);
                if (methodName != null) {
                    IServiceAction action = bizObj.requireAction(methodName);
                    map.put(methodName, buildActionFunction(action, method, bizObj.getBizObjName()));
                } else {
                    for (Object bizModelBean : bizObj.getBizModelBeans()) {
                        if (bizModelBean.getClass().isInstance(clazz)) {
                            map.put(methodName, buildBeanModelInvoker(bizModelBean, method));
                            break;
                        }
                    }
                }
            }
        }
        return map;
    }

    static Function<Object[], Object> buildBeanModelInvoker(Object bizModelBean, IFunctionModel method) {
        int contextIndex = -1;
        for (int i = 0, n = method.getArgCount(); i < n; i++) {
            IFunctionArgument arg = method.getArgs().get(i);
            if (IEvalContext.class.isAssignableFrom(arg.getRawClass())) {
                contextIndex = i;
                break;
            }
        }
        int index = contextIndex;
        return args -> {
            IEvalContext context = index >= 0 ? ((IEvalContext) args[index]) : IServiceContext.getCtx();
            return method.invoke(bizModelBean, args, context == null ? DisabledEvalScope.INSTANCE : context.getEvalScope());
        };
    }

    static Function<Object[], Object> buildActionFunction(IServiceAction action, IFunctionModel method, String bizObjName) {
        int selectionIndex = -1;
        int contextIndex = -1;
        int apiRequestIndex = -1;
        int requestBeanIndex = -1;
        String[] paramNames = new String[method.getArgCount()];
        for (int i = 0, n = method.getArgCount(); i < n; i++) {
            IFunctionArgument arg = method.getArgs().get(i);
            if (arg.getRawClass() == FieldSelectionBean.class) {
                selectionIndex = i;
            } else if (IExecutionContext.class.isAssignableFrom(arg.getRawClass())) {
                contextIndex = i;
            } else if (arg.getRawClass() == ApiRequest.class) {
                apiRequestIndex = i;
            } else if (arg.isAnnotationPresent(RequestBean.class)) {
                requestBeanIndex = i;
            } else {
                if (!arg.isAnnotationPresent(Name.class)) {
                    throw new NopException(ERR_GRAPHQL_METHOD_PARAM_NO_REFLECTION_NAME_ANNOTATION).loc(method.getLocation())
                            .param(ARG_OBJ_NAME, bizObjName).param(ARG_METHOD_NAME, method.getName())
                            .param(ARG_ARG_NAME, arg.getName());
                }
                paramNames[i] = arg.getName();
            }
        }
        return buildFunction(selectionIndex, contextIndex, apiRequestIndex, requestBeanIndex, paramNames, action);
    }

    /**
     * 构建函数，将接口方法参数转换为IServiceAction.invoke所需的参数
     *
     * @param selectionIndex   FieldSelectionBean参数在参数数组中的索引，-1表示不存在
     * @param contextIndex     IExecutionContext参数在参数数组中的索引，-1表示不存在
     * @param apiRequestIndex  ApiRequest参数在参数数组中的索引，-1表示不存在
     * @param requestBeanIndex RequestBean参数在参数数组中的索引，-1表示不存在
     * @param paramNames       普通参数的名称数组，对应IServiceAction.invoke的request参数
     * @param action           要调用的IServiceAction
     * @return 转换函数
     */
    static Function<Object[], Object> buildFunction(int selectionIndex, int contextIndex,
                                                    int apiRequestIndex, int requestBeanIndex,
                                                    String[] paramNames, IServiceAction action) {
        return args -> {
            // 提取各种特殊参数
            FieldSelectionBean selection = selectionIndex >= 0 ? (FieldSelectionBean) args[selectionIndex] : null;
            IServiceContext context = contextIndex >= 0 ? (IServiceContext) args[contextIndex] : IServiceContext.getCtx();
            ApiRequest apiRequest = apiRequestIndex >= 0 ? (ApiRequest) args[apiRequestIndex] : null;

            // 构建request对象
            Object request;

            // 如果存在ApiRequest参数，直接使用它作为request
            if (apiRequestIndex >= 0) {
                request = apiRequest.getData();
                if (selection == null)
                    selection = apiRequest.getSelection();
            } else if (requestBeanIndex >= 0) {
                // 如果存在RequestBean参数，使用它作为request
                request = args[requestBeanIndex];
            } else {
                // 否则根据paramNames构建Map作为request
                request = buildRequestFromParams(args, paramNames);
            }

            // 调用action
            return action.invoke(request, selection, context);
        };
    }

    /**
     * 从参数数组中提取命名参数，构建request Map
     *
     * @param args       参数数组
     * @param paramNames 参数名称数组，paramNames[i]对应args[i]，null表示该位置不是命名参数
     * @return request Map
     */
    private static Map<String, Object> buildRequestFromParams(Object[] args, String[] paramNames) {
        Map<String, Object> request = new HashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            if (paramName != null) {
                request.put(paramName, args[i]);
            }
        }
        return request;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return ReflectionHelper.invokeObjectMethod(this, proxy, method, args);
        }

        Function<Object[], Object> fn = callers.get(method.getName());
        if (fn != null)
            return fn.apply(args);

        if (method.getParameterCount() == 3 && method.getName().equals("method_invoke")) {
            return bizObject.method_invoke((String) args[0], (Object[]) args[1], (IEvalScope) args[2]);
        }

        if (method.isDefault()) {
            return ReflectionHelper.invokeDefaultMethod(proxy, method, args);
        }

        throw new IllegalArgumentException("unsupported-method:" + method);
    }
}
