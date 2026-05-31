package io.nop.code.service;

import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeInheritance;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.model.CodeRelationType;
import io.nop.code.service.api.dto.TypeHierarchyDTO;
import io.nop.code.service.api.dto.SymbolInfoDTO;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestBuildHierarchyCycleProtection {

    private static final int MAX_HIERARCHY_DEPTH = 50;

    @Test
    void testCycleDoesNotCauseInfiniteRecursion() {
        SymbolTable table = new SymbolTable();
        CodeSymbol a = makeSymbol("id_a", "com.example.A", "A");
        CodeSymbol b = makeSymbol("id_b", "com.example.B", "B");
        CodeSymbol c = makeSymbol("id_c", "com.example.C", "C");
        table.add(a);
        table.add(b);
        table.add(c);

        List<CodeInheritance> inheritances = new ArrayList<>();
        inheritances.add(makeInheritance("id_b", "com.example.A"));
        inheritances.add(makeInheritance("id_c", "com.example.B"));
        inheritances.add(makeInheritance("id_a", "com.example.C"));

        Set<String> visited = new HashSet<>();
        TypeHierarchyDTO result = buildHierarchy("com.example.A", "super",
                MAX_HIERARCHY_DEPTH, table, inheritances, visited);

        assertNotNull(result);
        assertNoCycleInHierarchy(result, new HashSet<>());
    }

    @Test
    void testSelfReferencingCycle() {
        SymbolTable table = new SymbolTable();
        CodeSymbol a = makeSymbol("id_a", "com.example.A", "A");
        table.add(a);

        List<CodeInheritance> inheritances = new ArrayList<>();
        inheritances.add(makeInheritance("id_a", "com.example.A"));

        Set<String> visited = new HashSet<>();
        TypeHierarchyDTO result = buildHierarchy("com.example.A", "super",
                MAX_HIERARCHY_DEPTH, table, inheritances, visited);

        assertNotNull(result);
        assertNull(result.getSuperTypes(), "Self-referencing cycle should stop");
    }

    @Test
    void testDepthLimitRespected() {
        SymbolTable table = new SymbolTable();
        List<CodeInheritance> inheritances = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String name = "com.example.Level" + i;
            String nextName = "com.example.Level" + (i + 1);
            table.add(makeSymbol("id_" + i, name, "Level" + i));
            inheritances.add(makeInheritance("id_" + i, nextName));
        }
        table.add(makeSymbol("id_60", "com.example.Level60", "Level60"));

        Set<String> visited = new HashSet<>();
        TypeHierarchyDTO result = buildHierarchy("com.example.Level0", "super",
                MAX_HIERARCHY_DEPTH, table, inheritances, visited);

        assertNotNull(result);
        int maxDepth = measureMaxDepth(result);
        assertTrue(maxDepth <= MAX_HIERARCHY_DEPTH,
                "Hierarchy depth should not exceed " + MAX_HIERARCHY_DEPTH + " but was " + maxDepth);
    }

    private TypeHierarchyDTO buildHierarchy(String qualifiedName, String direction, int maxDepth,
                                             SymbolTable table, List<CodeInheritance> allInheritances,
                                             Set<String> visited) {
        if (visited.contains(qualifiedName)) return null;
        if (maxDepth <= 0) return null;
        visited.add(qualifiedName);

        CodeSymbol symbol = table.getByQualifiedName(qualifiedName);
        TypeHierarchyDTO node = new TypeHierarchyDTO();
        SymbolInfoDTO info = new SymbolInfoDTO();
        if (symbol != null) {
            info.setName(symbol.getName());
            info.setQualifiedName(symbol.getQualifiedName());
        } else {
            info.setQualifiedName(qualifiedName);
        }
        node.setSymbol(info);

        if ("super".equals(direction) || "both".equals(direction)) {
            List<TypeHierarchyDTO> superTypes = new ArrayList<>();
            for (CodeInheritance inh : allInheritances) {
                if (symbol != null && symbol.getId().equals(inh.getSubTypeId())) {
                    String superQn = inh.getSuperTypeQualifiedName();
                    TypeHierarchyDTO superNode = buildHierarchy(superQn, direction,
                            maxDepth - 1, table, allInheritances, visited);
                    if (superNode != null) superTypes.add(superNode);
                }
            }
            node.setSuperTypes(superTypes.isEmpty() ? null : superTypes);
        }
        return node;
    }

    private void assertNoCycleInHierarchy(TypeHierarchyDTO node, Set<String> seen) {
        String qn = node.getSymbol().getQualifiedName();
        assertFalse(seen.contains(qn), "Cycle detected at: " + qn);
        seen.add(qn);
        if (node.getSuperTypes() != null) {
            for (TypeHierarchyDTO child : node.getSuperTypes()) {
                assertNoCycleInHierarchy(child, new HashSet<>(seen));
            }
        }
    }

    private int measureMaxDepth(TypeHierarchyDTO node) {
        if (node.getSuperTypes() == null || node.getSuperTypes().isEmpty()) return 1;
        int maxChild = 0;
        for (TypeHierarchyDTO child : node.getSuperTypes()) {
            maxChild = Math.max(maxChild, measureMaxDepth(child));
        }
        return 1 + maxChild;
    }

    private CodeSymbol makeSymbol(String id, String qualifiedName, String name) {
        CodeSymbol sym = new CodeSymbol();
        sym.setId(id);
        sym.setQualifiedName(qualifiedName);
        sym.setName(name);
        sym.setKind(CodeSymbolKind.CLASS);
        return sym;
    }

    private CodeInheritance makeInheritance(String subTypeId, String superTypeQualifiedName) {
        CodeInheritance inh = new CodeInheritance();
        inh.setSubTypeId(subTypeId);
        inh.setSuperTypeQualifiedName(superTypeQualifiedName);
        inh.setRelationType(CodeRelationType.EXTENDS);
        return inh;
    }
}
