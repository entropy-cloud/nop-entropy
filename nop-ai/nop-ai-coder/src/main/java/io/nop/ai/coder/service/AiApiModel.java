package io.nop.ai.coder.service;

import io.nop.ai.coder.AiCoderConstants;
import io.nop.ai.coder.utils.AiCoderHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.type.IGenericType;
import io.nop.report.core.util.ExcelReportHelper;
import io.nop.rpc.model.ApiMessageModel;
import io.nop.rpc.model.ApiMethodModel;
import io.nop.rpc.model.ApiModel;
import io.nop.rpc.model.ApiServiceModel;
import io.nop.rpc.model.RpcModelConstants;
import io.nop.xlang.xdsl.DslModelHelper;

import java.util.ArrayList;
import java.util.List;

public class AiApiModel {
    private XNode apiNodeForApi;
    private final XNode apiNode;
    private ApiModel apiModel;

    public AiApiModel(XNode apiNode) {
        this.apiNode = apiNode;
    }

    public static AiApiModel buildFromApiNode(XNode node) {
        return new AiApiModel(node);
    }

    public static AiApiModel buildFromApiModelPath(String path) {
        if (path.endsWith(".xlsx")) {
            IResource resource = VirtualFileSystem.instance().getResource(path);
            ApiModel apiModel = (ApiModel) ExcelReportHelper.loadXlsxObject(RpcModelConstants.API_IMPL_PATH, resource);
            return buildFromApiModel(apiModel);
        }
        XNode node = XNodeParser.instance().parseFromVirtualPath(path);
        return buildFromApiNode(node);
    }

    public static AiApiModel buildFromApiModel(ApiModel apiModel) {
        XNode node = DslModelHelper.dslModelToXNode(RpcModelConstants.XDSL_SCHEMA_API, apiModel);
        AiApiModel model = new AiApiModel(node);
        model.apiModel = apiModel;
        return model;
    }

    public XNode getApiNode() {
        return apiNode;
    }

    public ApiModel getApiModelBean() {
        if (apiModel == null) {
            apiModel = (ApiModel) DslModelHelper.parseDslModelNode(RpcModelConstants.XDSL_SCHEMA_API, apiNode);
        }
        return apiModel;
    }

    public String getApiNodeXml() {
        return apiNode.xml();
    }

    public XNode getApiNodeForAi() {
        if (apiNodeForApi != null)
            apiNodeForApi = AiCoderHelper.extractDslNode(AiCoderConstants.SCHEMA_AI_API, apiNode);
        return apiNodeForApi;
    }

    public List<String> getServiceNames() {
        List<String> ret = new ArrayList<>();
        ApiModel apiModel = getApiModelBean();
        for (ApiServiceModel serviceModel : apiModel.getServices()) {
            ret.add(serviceModel.getName());
        }
        return ret;
    }

    public ApiServiceModel getServiceModel(String serviceName) {
        return getApiModelBean().getService(serviceName);
    }

    public ApiMethodModel getServiceMethodModel(String serviceName, String methodName) {
        return getServiceModel(serviceName).getMethod(methodName);
    }

    public XNode getServiceNode(String serviceName) {
        XNode servicesNode = getApiNodeForAi().childByTag("services");
        if (servicesNode == null)
            return null;
        XNode serviceNode = servicesNode.childByAttr("name", serviceName);
        return serviceNode;
    }

    public XNode getServiceMethodNode(String serviceName, String methodName) {
        XNode serviceNode = getServiceNode(serviceName);
        if (serviceNode == null)
            return null;
        return serviceNode.childByAttr("name", methodName);
    }

    public ApiMessageModel getMessageModel(String messageName) {
        return getApiModelBean().getMessage(messageName);
    }

    public List<ApiMessageModel> getMethodMessages(String serviceName, String methodName) {
        ApiMethodModel method = getServiceMethodModel(serviceName, methodName);
        ApiMessageModel request = getMessageModel(method.getRequestMessage());
        List<ApiMessageModel> ret = new ArrayList<>();
        if (request != null)
            ret.add(request);

        IGenericType type = method.getResponseMessage();
        if (type != null) {
            ApiMessageModel response = getMessageModel(type.toString());
            if (response == null && (type.isMapLike() || type.isCollectionLike()))
                response = getMessageModel(type.getComponentType().toString());
            if (response != null)
                ret.add(response);
        }
        return ret;
    }

    public String getMethodJava(String serviceName, String methodName) {
        ApiModelToJava toJava = new ApiModelToJava();
        ApiServiceModel serviceModel = getServiceModel(serviceName);

        ApiMethodModel method = serviceModel.getMethod(methodName);
        toJava.beginService(serviceModel);
        toJava.appendMethodModel(method);
        toJava.endService();

        for (ApiMessageModel message : getMethodMessages(serviceName, methodName)) {
            toJava.appendMessageModel(message);
        }
        return toJava.toString();
    }

    public List<MethodInfo> getMethodInfos() {
        List<MethodInfo> ret = new ArrayList<>();
        for (ApiServiceModel service : getApiModelBean().getServices()) {
            for (ApiMethodModel method : service.getMethods()) {
                ret.add(new MethodInfo(service.getName(), method.getName()));
            }
        }
        return ret;
    }
}