/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.parse;

import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLInterfaceDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test GraphQL Interface and Implements support
 */
public class TestGraphQLInterface extends BaseTestCase {

    @Test
    public void testInterfaceParsing() {
        String schema = "interface Node {\n" +
                "  id: ID!\n" +
                "  name: String!\n" +
                "}\n" +
                "\n" +
                "type Dog implements Node {\n" +
                "  id: ID!\n" +
                "  name: String!\n" +
                "  breed: String\n" +
                "}";

        GraphQLDocumentParser parser = new GraphQLDocumentParser();
        GraphQLDocument doc = parser.parseFromText(null, schema);
        
        assertNotNull(doc, "Document should not be null");
        assertNotNull(doc.getDefinitions(), "Definitions should not be null");
        assertEquals(2, doc.getDefinitions().size(), "Should parse 2 definitions");
        
        boolean foundInterface = false;
        for (GraphQLDefinition def : doc.getDefinitions()) {
            if (def instanceof GraphQLInterfaceDefinition) {
                foundInterface = true;
                GraphQLInterfaceDefinition iface = (GraphQLInterfaceDefinition) def;
                assertEquals("Node", iface.getName());
                assertNotNull(iface.getFields(), "Interface fields should not be null");
                assertEquals(2, iface.getFields().size(), "Should have 2 fields");
            }
        }
        assertTrue(foundInterface, "Interface definition should be parsed");
    }

    @Test
    public void testImplementsClause() {
        String schema = "interface Node {\n" +
                "  id: ID!\n" +
                "}\n" +
                "\n" +
                "type Dog implements Node {\n" +
                "  id: ID!\n" +
                "  name: String!\n" +
                "}";

        GraphQLDocumentParser parser = new GraphQLDocumentParser();
        GraphQLDocument doc = parser.parseFromText(null, schema);
        
        boolean foundDog = false;
        for (GraphQLDefinition def : doc.getDefinitions()) {
            if (def instanceof GraphQLObjectDefinition) {
                GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) def;
                if ("Dog".equals(objDef.getName())) {
                    foundDog = true;
                    assertNotNull(objDef.getInterfaces(), "Dog should implement Node");
                    assertFalse(objDef.getInterfaces().isEmpty(), "Interfaces list should not be empty");
                    assertEquals(1, objDef.getInterfaces().size(), "Should implement 1 interface");
                }
            }
        }
        assertTrue(foundDog, "Dog type should be found with implements");
    }

    @Test
    public void testMultipleInterfacesWithAmpersand() {
        String schema = "interface A {\n" +
                "  fieldA: String\n" +
                "}\n" +
                "\n" +
                "interface B {\n" +
                "  fieldB: String\n" +
                "}\n" +
                "\n" +
                "type C implements A & B {\n" +
                "  fieldA: String\n" +
                "  fieldB: String\n" +
                "}";

        GraphQLDocumentParser parser = new GraphQLDocumentParser();
        GraphQLDocument doc = parser.parseFromText(null, schema);
        
        for (GraphQLDefinition def : doc.getDefinitions()) {
            if (def instanceof GraphQLObjectDefinition) {
                GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) def;
                if ("C".equals(objDef.getName())) {
                    assertNotNull(objDef.getInterfaces(), "C should implement interfaces");
                    assertEquals(2, objDef.getInterfaces().size(), "C should implement 2 interfaces");
                }
            }
        }
    }

    @Test
    public void testInterfaceFieldDefinitions() {
        String schema = "interface Node {\n" +
                "  id: ID!\n" +
                "  name: String!\n" +
                "  description: String\n" +
                "}";

        GraphQLDocumentParser parser = new GraphQLDocumentParser();
        GraphQLDocument doc = parser.parseFromText(null, schema);
        
        for (GraphQLDefinition def : doc.getDefinitions()) {
            if (def instanceof GraphQLInterfaceDefinition) {
                GraphQLInterfaceDefinition iface = (GraphQLInterfaceDefinition) def;
                
                assertNotNull(iface.getField("id"), "Should have 'id' field");
                assertNotNull(iface.getField("name"), "Should have 'name' field");
                assertNotNull(iface.getField("description"), "Should have 'description' field");
                assertNull(iface.getField("nonexistent"), "Should return null for nonexistent field");
                
                assertEquals("id", iface.getField("id").getName());
                assertEquals("name", iface.getField("name").getName());
                assertEquals("description", iface.getField("description").getName());
            }
        }
    }

    @Test
    public void testUserSchemaWithInterfaces() {
        String schema = "interface NopCodeNode {\n" +
                "  id: ID!\n" +
                "}\n" +
                "\n" +
                "type NopCodeSymbol implements NopCodeNode {\n" +
                "  id: ID!\n" +
                "  name: String!\n" +
                "}";

        GraphQLDocumentParser parser = new GraphQLDocumentParser();
        GraphQLDocument doc = parser.parseFromText(null, schema);
        
        assertNotNull(doc, "Document should not be null");
        assertEquals(2, doc.getDefinitions().size(), "Should parse 2 definitions");
        
        boolean foundInterface = false;
        boolean foundImplements = false;
        
        for (GraphQLDefinition def : doc.getDefinitions()) {
            if (def instanceof GraphQLInterfaceDefinition) {
                foundInterface = true;
                assertEquals("NopCodeNode", ((GraphQLInterfaceDefinition) def).getName());
            } else if (def instanceof GraphQLObjectDefinition) {
                GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) def;
                if (objDef.getInterfaces() != null && !objDef.getInterfaces().isEmpty()) {
                    foundImplements = true;
                    assertEquals(1, objDef.getInterfaces().size());
                    assertEquals("NopCodeNode", objDef.getInterfaces().get(0).getName());
                }
            }
        }
        
        assertTrue(foundInterface, "Interface NopCodeNode should be parsed");
        assertTrue(foundImplements, "NopCodeSymbol should implement NopCodeNode");
    }
}
