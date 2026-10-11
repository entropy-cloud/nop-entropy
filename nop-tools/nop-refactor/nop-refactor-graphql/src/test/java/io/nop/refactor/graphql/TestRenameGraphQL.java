package io.nop.refactor.graphql;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.GraphQLEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WI12 rename-face RPC end-to-end proof (plan 12 Phase 1, Minimum Rules
 * #22/#23): {@code Refactor__previewRename} and {@code Refactor__applyRename}
 * through the real GraphQLEngine with the container-wired bean — the beans
 * wiring carries the nop-refactor-java adapter (the RESOLVED payload is the
 * wiring proof: an unwired resolver fails the face's structured pre-check).
 * The cross-file rename lands byte-for-byte with the symbol-intact assertion
 * non-null; the CONFLICT refusal surfaces as a structured nonApplied entry
 * with a null assertion and zero edits.
 */
class TestRenameGraphQL {

    private static final String SERVICE_PATH = "a/Greet.java";
    private static final String USER_PATH = "a/GreetUser.java";

    private static final String SERVICE_SOURCE = """
            package a;

            public class Greet {
                public String hi() {
                    return "hi";
                }
            }
            """;

    private static final String USER_SOURCE = """
            package a;

            public class GreetUser {
                Greet greet;

                String use() {
                    return greet.hi();
                }
            }
            """;

    static GraphQLEngine engine;
    static NopRefactorBizModel bizModel;

    @BeforeAll
    public static void init() throws Exception {
        CoreInitialization.initialize();
        bizModel = BeanContainer.getBeanByType(NopRefactorBizModel.class);
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new RenameSchemaLoader(bizModel));
        engine.init();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * The WI6 loader plus the RenameInput definitions (the double-name
     * pattern: the plain and the generated qualified name).
     */
    static class RenameSchemaLoader extends TestNopRefactorGraphQL.BizModelSchemaLoader {
        RenameSchemaLoader(Object... beans) {
            super(beans);
        }

        private io.nop.graphql.core.ast.GraphQLObjectDefinition renameInputDef(String name) {
            io.nop.graphql.core.ast.GraphQLObjectDefinition def =
                    new io.nop.graphql.core.ast.GraphQLObjectDefinition();
            def.setName(name);
            def.setFields(new java.util.ArrayList<>(List.of(
                    field("paths", null, "String"), field("fqn", null, "String"),
                    field("path", null, "String"), field("byteOffset", null, "Int"),
                    field("newName", null, "String"))));
            return def;
        }

        private io.nop.graphql.core.ast.GraphQLFieldDefinition field(String name,
                                                                     String listElement,
                                                                     String scalar) {
            io.nop.graphql.core.ast.GraphQLFieldDefinition field =
                    new io.nop.graphql.core.ast.GraphQLFieldDefinition();
            field.setName(name);
            if (listElement != null) {
                io.nop.graphql.core.ast.GraphQLListType list =
                        new io.nop.graphql.core.ast.GraphQLListType();
                io.nop.graphql.core.ast.GraphQLNamedType elementType =
                        new io.nop.graphql.core.ast.GraphQLNamedType();
                elementType.setName(listElement);
                list.setType(elementType);
                field.setType(list);
            } else {
                io.nop.graphql.core.ast.GraphQLNamedType type =
                        new io.nop.graphql.core.ast.GraphQLNamedType();
                type.setName(scalar);
                field.setType(type);
            }
            return field;
        }

        @Override
        public io.nop.graphql.core.ast.GraphQLObjectDefinition getObjectTypeDefinition(
                String objName) {
            io.nop.graphql.core.ast.GraphQLObjectDefinition def =
                    super.getObjectTypeDefinition(objName);
            if (def != null) {
                return def;
            }
            if (objName.equals("RenameInput")
                    || objName.equals("g_io_nop_refactor_graphql_RenameInput")) {
                return renameInputDef(objName);
            }
            return null;
        }

        @Override
        public io.nop.graphql.core.ast.GraphQLObjectDefinition resolveTypeDefinition(
                io.nop.graphql.core.ast.GraphQLType type) {
            io.nop.graphql.core.ast.GraphQLObjectDefinition def =
                    super.resolveTypeDefinition(type);
            if (def != null) {
                return def;
            }
            if (type.getNamedTypeName().equals("RenameInput")
                    || type.getNamedTypeName().equals("g_io_nop_refactor_graphql_RenameInput")) {
                return renameInputDef(type.getNamedTypeName());
            }
            return null;
        }
    }

    private static Path write(String relPath, String content) throws Exception {
        Path file = Path.of("target", "rename-graphql", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private record Written(Path service, Path user, String serviceDisk,
                           String userDisk) {
    }

    private static Written writeModule(String subdir) throws Exception {
        Path service = write(subdir + "/" + SERVICE_PATH, SERVICE_SOURCE);
        Path user = write(subdir + "/" + USER_PATH, USER_SOURCE);
        return new Written(service, user, service.toString(), user.toString());
    }

    private static Map<String, Object> renameInput(Written written, String newName) {
        Map<String, Object> inner = new HashMap<>();
        inner.put("paths", List.of(written.serviceDisk(), written.userDisk()));
        inner.put("path", written.userDisk());
        inner.put("byteOffset", USER_SOURCE.indexOf("greet;"));
        inner.put("newName", newName);
        Map<String, Object> data = new HashMap<>();
        data.put("input", inner);
        return data;
    }

    private static Map<String, Object> fqnRenameInput(Written written, String newName) {
        Map<String, Object> inner = new HashMap<>();
        inner.put("paths", List.of(written.serviceDisk(), written.userDisk()));
        inner.put("fqn", "a.Greet");
        inner.put("newName", newName);
        Map<String, Object> data = new HashMap<>();
        data.put("input", inner);
        return data;
    }

    private static ApiResponse<?> rpc(String action, Map<String, Object> data) {
        IGraphQLExecutionContext context = engine.newRpcContext(
                GraphQLOperationType.mutation, action, request(data));
        return FutureHelper.syncGet(engine.executeRpcAsync(context));
    }

    private static ApiRequest<Map<String, Object>> request(Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        return request;
    }

    @Test
    void previewRenameCarriesNonNullSymbolIntactAndWritesNothing() throws Exception {
        Written written = writeModule("preview");

        ApiResponse<?> response = rpc("Refactor__previewRename",
                renameInput(written, "salute"));

        assertTrue(response.isOk(), String.valueOf(response));
        String body = String.valueOf(response.get());
        assertTrue(body.contains("applied=false"), body);
        assertTrue(body.contains("symbolIntact=true"),
                "the RESOLVED rename reports its assertion: " + body);
        assertEquals(SERVICE_SOURCE, Files.readString(written.service()),
                "preview wrote nothing");
        assertEquals(USER_SOURCE, Files.readString(written.user()),
                "preview wrote nothing");
    }

    @Test
    void applyRenameLandsCrossFileAndCarriesSymbolIntact() throws Exception {
        Written written = writeModule("apply");

        ApiResponse<?> response = rpc("Refactor__applyRename",
                renameInput(written, "salute"));

        assertTrue(response.isOk(), String.valueOf(response));
        assertTrue(String.valueOf(response.get()).contains("applied=true"),
                "apply reports applied=true: " + response);
        assertEquals(SERVICE_SOURCE, Files.readString(written.service()),
                "the Service file carries no `greet` reference — untouched");
        assertEquals(USER_SOURCE.replace("Greet greet;", "Greet salute;")
                        .replace("return greet.hi();", "return salute.hi();"),
                Files.readString(written.user()),
                "the field and its bound use renamed cross-file (the type `Greet` "
                        + "is a different symbol and stays)");
        assertTrue(String.valueOf(response.get()).contains("symbolIntact=true"),
                "the assertion rides the apply payload");
    }

    @Test
    void fqnLocatorRenamesThroughTheIndex() throws Exception {
        Written written = writeModule("fqn");

        ApiResponse<?> response = rpc("Refactor__applyRename",
                fqnRenameInput(written, "Salutation"));

        assertTrue(response.isOk(), String.valueOf(response));
        assertEquals(SERVICE_SOURCE.replace("public class Greet {",
                "public class Salutation {"),
                Files.readString(written.service()),
                "the TYPE rename touches the type face only");
        assertEquals(USER_SOURCE.replace("Greet greet;", "Salutation greet;"),
                Files.readString(written.user()),
                "the cross-file type use renamed; the GreetUser class name and "
                        + "the greet field are different symbols");
    }

    @Test
    void conflictRefusalSurfacesAsStructuredNonApplyWithNullAssertion()
            throws Exception {
        // plan 12 adjudication 8: an overloaded same-name method pair — the
        // member face's ambiguity refusal
        String source = SERVICE_SOURCE.replace(
                "    public String hi() {",
                "    public String hi() {\n        return \"hi\";\n    }\n\n"
                        + "    public String hi(String name) {\n"
                        + "        return name;\n");
        Path service = write("conflict/" + SERVICE_PATH, source);
        Path user = write("conflict/" + USER_PATH, USER_SOURCE);
        String serviceDisk = service.toString();
        String userDisk = user.toString();

        Map<String, Object> inner = new HashMap<>();
        inner.put("paths", List.of(serviceDisk, userDisk));
        inner.put("path", serviceDisk);
        inner.put("byteOffset", source.indexOf("String hi()") + 7);
        inner.put("newName", "salute");
        Map<String, Object> data = new HashMap<>();
        data.put("input", inner);

        ApiResponse<?> response = rpc("Refactor__applyRename", data);

        assertTrue(response.isOk(), "a plan-level refusal is a structured payload: "
                + response);
        String body = String.valueOf(response.get());
        assertTrue(body.contains("CONFLICT"), body);
        assertTrue(body.contains("symbolIntact=null"),
                "a refused rename has no assertion: " + body);
        assertEquals(source, Files.readString(service),
                "the refused rename writes nothing");
    }
}
