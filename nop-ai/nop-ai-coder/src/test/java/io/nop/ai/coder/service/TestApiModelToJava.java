package io.nop.ai.coder.service;

import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.rpc.model.ApiMessageFieldModel;
import io.nop.rpc.model.ApiMessageModel;
import io.nop.rpc.model.ApiMethodModel;
import io.nop.rpc.model.ApiModel;
import io.nop.rpc.model.ApiServiceModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestApiModelToJava {

    private static IGenericType type(String name) {
        return GenericTypeHelper.buildRawType(name);
    }

    private static ApiModel buildSampleModel() {
        ApiModel apiModel = new ApiModel();

        ApiServiceModel service = new ApiServiceModel();
        service.setName("MyService");
        service.setDisplayName("我的服务");
        service.setDescription("Test service description");

        ApiMethodModel method = new ApiMethodModel();
        method.setName("echo");
        method.setDisplayName("回声");
        method.setRequestMessage("EchoRequest");
        method.setResponseMessage(type("EchoResponse"));
        service.addMethod(method);

        ApiMethodModel mutation = new ApiMethodModel();
        mutation.setName("update");
        mutation.setRequestMessage("UpdateRequest");
        mutation.setResponseMessage(type("void"));
        mutation.setMutation(true);
        service.addMethod(mutation);

        apiModel.addService(service);

        ApiMessageModel request = new ApiMessageModel();
        request.setName("EchoRequest");
        ApiMessageFieldModel field = new ApiMessageFieldModel();
        field.setName("text");
        field.setType(type("java.lang.String"));
        request.addField(field);
        apiModel.addMessage(request);

        ApiMessageModel response = new ApiMessageModel();
        response.setName("EchoResponse");
        ApiMessageFieldModel result = new ApiMessageFieldModel();
        result.setName("result");
        result.setType(type("java.lang.String"));
        response.addField(result);
        apiModel.addMessage(response);

        return apiModel;
    }

    @Test
    public void testAppendApiModel() {
        String code = new ApiModelToJava().appendApiModel(buildSampleModel()).toString();

        assertTrue(code.contains("interface MyService{"));
        assertTrue(code.contains("我的服务:"));
        assertTrue(code.contains("Test service description"));
        assertTrue(code.contains("EchoResponse echo(@RequestBean EchoRequest request);"));
        assertTrue(code.contains("@BizMutation"));
        assertTrue(code.contains("void update(@RequestBean UpdateRequest request);"));
        assertTrue(code.contains("class EchoRequest{"));
        assertTrue(code.contains("    String text;"));
        assertTrue(code.contains("class EchoResponse{"));
    }

    @Test
    public void testSelectedServiceFilter() {
        ApiModelToJava toJava = new ApiModelToJava(Set.of("OtherService"), null);
        String code = toJava.appendApiModel(buildSampleModel()).toString();

        assertFalse(code.contains("interface MyService{"));
        assertTrue(code.contains("class EchoRequest{"), "messages are appended regardless of service selection");
    }

    @Test
    public void testSelectedMethodFilter() {
        ApiModelToJava toJava = new ApiModelToJava(null, Set.of("echo"));
        String code = toJava.appendApiModel(buildSampleModel()).toString();

        assertTrue(code.contains("EchoResponse echo(@RequestBean EchoRequest request);"));
        assertFalse(code.contains("@BizMutation"));
    }

    @Test
    public void testAppendMessageModel() {
        ApiMessageModel message = new ApiMessageModel();
        message.setName("TestMessage");
        ApiMessageFieldModel field = new ApiMessageFieldModel();
        field.setName("count");
        field.setType(type("java.lang.Integer"));
        message.addField(field);

        String code = new ApiModelToJava().appendMessageModel(message).toString();

        assertEquals("class TestMessage{\n" +
                "    Integer count;\n" +
                "}\n\n", code);
    }

    @Test
    public void testAppendServiceModel() {
        ApiServiceModel service = new ApiServiceModel();
        service.setName("PlainService");
        ApiMethodModel method = new ApiMethodModel();
        method.setName("ping");
        method.setRequestMessage("PingRequest");
        method.setResponseMessage(type("PingResponse"));
        service.addMethod(method);

        String code = new ApiModelToJava().appendServiceModel(service).toString();

        assertTrue(code.startsWith("/**  */\ninterface PlainService{"), "unexpected output: " + code);
        assertTrue(code.contains("PingResponse ping(@RequestBean PingRequest request);"));
        assertTrue(code.endsWith("}\n\n"));
        assertEquals(1, countOccurrences(code, "PingResponse ping"));
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int pos = 0;
        while ((pos = text.indexOf(token, pos)) >= 0) {
            count++;
            pos += token.length();
        }
        return count;
    }

    @Test
    public void testEmptyApiModel() {
        ApiModel apiModel = new ApiModel();
        String code = new ApiModelToJava().appendApiModel(apiModel).toString();
        assertEquals("", code);
    }

    @Test
    public void testMethodListRoundTrip() {
        ApiModel apiModel = buildSampleModel();
        assertEquals(2, apiModel.getServices().get(0).getMethods().size());
        assertEquals(List.of("echo", "update"),
                apiModel.getServices().get(0).getMethods().stream().map(ApiMethodModel::getName)
                        .collect(java.util.stream.Collectors.toList()));
    }
}
