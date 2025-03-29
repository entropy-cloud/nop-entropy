package io.nop.tcc.core.meta;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiHeaders;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.rpc.api.IRpcProxyFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultTccServiceMetaLoader implements ITccServiceMetaLoader {
    static final Logger LOG = LoggerFactory.getLogger(DefaultTccServiceMetaLoader.class);

    private List<IRpcProxyFactory> rpcProxyFactories;

    private final Map<String, TccServiceMeta> serviceMetas = new HashMap<>();

    private boolean guessTccMethod = false;

    public void setRpcProxyFactories(List<IRpcProxyFactory> rpcProxyFactories) {
        this.rpcProxyFactories = rpcProxyFactories;
    }

    @InjectValue("@cfg:nop.tcc.guess-tcc-method|false")
    public void setGuessTccMethod(boolean guessTccMethod) {
        this.guessTccMethod = guessTccMethod;
    }

    @PostConstruct
    public void init() {
        discoverFromProxyFactories();

        discoverFromResources();

        if (LOG.isInfoEnabled())
            LOG.info("nop.tcc.meta=\n{}", JsonTool.serialize(serviceMetas, true));
    }

    protected void discoverFromProxyFactories() {
        if (rpcProxyFactories != null) {
            for (IRpcProxyFactory factory : rpcProxyFactories) {
                buildServiceMetaForFactory(factory);
            }
        }
    }

    protected void discoverFromResources() {
        List<IResource> resources = ModuleManager.instance().findModuleResources(false, "/tcc/meta", ".tcc-meta.json5");
        for (IResource resource : resources) {
            TccServiceMeta serviceMeta = JsonTool.parseBeanFromResource(resource, TccServiceMeta.class);
            mergeMethods(serviceMeta);
        }
    }

    protected void buildServiceMetaForFactory(IRpcProxyFactory factory) {
        TccServiceMeta serviceMeta = ReflectionTccServiceMetaBuilder.INSTANCE.build(factory.getServiceName(),
                factory.getServiceClass());
        mergeMethods(serviceMeta);
    }

    protected void mergeMethods(TccServiceMeta serviceMeta) {
        TccServiceMeta meta = serviceMetas.putIfAbsent(serviceMeta.getServiceName(), serviceMeta);
        if (meta != null)
            serviceMetas.put(meta.getServiceName(), meta.merge(serviceMeta.getMethods()));
    }

    @Override
    public TccMethodMeta getMethodMeta(String serviceName, String methodName, ApiRequest<?> request) {
        TccServiceMeta serviceMeta = serviceMetas.get(serviceName);
        if (serviceMeta != null)
            return serviceMeta.getMethod(methodName);

        if (!guessTccMethod)
            return null;

        String confirmMethod = ApiHeaders.getTccConfirm(request);
        String cancelMethod = ApiHeaders.getTccCancel(request);
        if (cancelMethod == null)
            cancelMethod = "cancel" + StringHelper.capitalize(methodName);

        return new TccMethodMeta(null, methodName, confirmMethod, cancelMethod);
    }
}