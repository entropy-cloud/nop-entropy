package io.nop.rpc.grpc.proto;

public class ServiceMethodSchema {
    private GenericObjSchema requestSchema;
    private GenericObjSchema responseSchema;

    public GenericObjSchema getRequestSchema() {
        return requestSchema;
    }

    public void setRequestSchema(GenericObjSchema requestSchema) {
        this.requestSchema = requestSchema;
    }

    public GenericObjSchema getResponseSchema() {
        return responseSchema;
    }

    public void setResponseSchema(GenericObjSchema responseSchema) {
        this.responseSchema = responseSchema;
    }
}
