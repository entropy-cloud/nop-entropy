package io.nop.rpc.grpc.proto.codegen;

import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.nop.commons.text.IndentPrinter;

public class ProtoCodeGenerator extends IndentPrinter {
    public ProtoCodeGenerator(Appendable out, int lineLength) {
        super(out, lineLength);
    }

    public ProtoCodeGenerator(int lineLength) {
        super(lineLength);
    }

    public void printService(ServerServiceDefinition service) {
        append("service ").append(service.getServiceDescriptor().getName()).append(" {");
        incIndent().br();
        for (MethodDescriptor method : service.getServiceDescriptor().getMethods()) {
            indent().append("rpc ");
            append(method.getFullMethodName()).append(" {");
            incIndent();

            decIndent();
        }
        decIndent();
    }
}
