package io.nop.code.service;

import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeInheritance;
import io.nop.code.core.model.CodeRelationType;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestTypeHierarchyAfterResolveQualifiedNames {

    private CodeSymbol createSymbol(String id, String qualifiedName) {
        CodeSymbol s = new CodeSymbol();
        s.setId(id);
        s.setQualifiedName(qualifiedName);
        s.setKind(CodeSymbolKind.CLASS);
        s.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        return s;
    }

    private CodeInheritance createInheritance(String subId, String superRef) {
        CodeInheritance inh = new CodeInheritance();
        inh.setSubTypeId(subId);
        inh.setSuperTypeQualifiedName(superRef);
        inh.setRelationType(CodeRelationType.EXTENDS);
        return inh;
    }

    @Test
    void testSuperTypeResolvedAsUuidCanBeLookedUpById() {
        SymbolTable table = new SymbolTable();
        CodeSymbol child = createSymbol("uuid-child", "com.example.Child");
        CodeSymbol base = createSymbol("uuid-base", "com.example.Base");
        table.add(child);
        table.add(base);

        List<CodeInheritance> inheritances = List.of(
                createInheritance("uuid-child", "uuid-base")
        );

        CodeSymbol currentSymbol = table.getByQualifiedName("com.example.Child");
        assertNotNull(currentSymbol);

        CodeInheritance inh = inheritances.get(0);

        assertTrue(currentSymbol.getId().equals(inh.getSubTypeId()));

        String superRef = inh.getSuperTypeQualifiedName();
        CodeSymbol superSymbol = table.getById(superRef);
        assertNotNull(superSymbol, "getById should resolve UUID back to symbol");
        assertEquals("com.example.Base", superSymbol.getQualifiedName());
    }

    @Test
    void testSubTypeDirectionMatchesByBothQualifiedNameAndId() {
        SymbolTable table = new SymbolTable();
        CodeSymbol base = createSymbol("uuid-base", "com.example.Base");
        CodeSymbol child = createSymbol("uuid-child", "com.example.Child");
        table.add(base);
        table.add(child);

        List<CodeInheritance> inheritances = List.of(
                createInheritance("uuid-child", "uuid-base")
        );

        String qualifiedName = "com.example.Base";
        String currentId = table.getByQualifiedName(qualifiedName).getId();

        long matchCount = inheritances.stream()
                .filter(i -> qualifiedName.equals(i.getSuperTypeQualifiedName())
                        || (currentId != null && currentId.equals(i.getSuperTypeQualifiedName())))
                .count();
        assertEquals(1, matchCount, "Sub-type direction should match via symbol ID");
    }

    @Test
    void testExternalSuperTypeRemainsAsQualifiedName() {
        SymbolTable table = new SymbolTable();
        CodeSymbol child = createSymbol("uuid-child", "com.example.Child");
        table.add(child);

        List<CodeInheritance> inheritances = List.of(
                createInheritance("uuid-child", "java.lang.Object")
        );

        CodeSymbol currentSymbol = table.getByQualifiedName("com.example.Child");
        CodeInheritance inh = inheritances.get(0);

        String superRef = inh.getSuperTypeQualifiedName();
        CodeSymbol superSymbol = table.getById(superRef);
        assertNull(superSymbol, "External type should not be in symbol table");

        String superQn = superSymbol != null ? superSymbol.getQualifiedName() : superRef;
        assertEquals("java.lang.Object", superQn, "Should fall back to treating as qualified name");
    }

    @Test
    void testFullHierarchyTraversalWithResolvedIds() {
        SymbolTable table = new SymbolTable();
        CodeSymbol grandParent = createSymbol("uuid-gp", "com.example.GrandParent");
        CodeSymbol parent = createSymbol("uuid-p", "com.example.Parent");
        CodeSymbol child = createSymbol("uuid-c", "com.example.Child");
        table.add(grandParent);
        table.add(parent);
        table.add(child);

        List<CodeInheritance> inheritances = List.of(
                createInheritance("uuid-c", "uuid-p"),
                createInheritance("uuid-p", "uuid-gp")
        );

        Set<String> visited = new HashSet<>();
        List<String> hierarchy = new ArrayList<>();
        collectSuperHierarchy("com.example.Child", table, inheritances, visited, hierarchy, 5);

        assertEquals(List.of("com.example.Parent", "com.example.GrandParent"), hierarchy);
    }

    private void collectSuperHierarchy(String qualifiedName, SymbolTable table,
                                        List<CodeInheritance> allInh, Set<String> visited,
                                        List<String> result, int maxDepth) {
        if (visited.contains(qualifiedName) || maxDepth <= 0) return;
        visited.add(qualifiedName);

        CodeSymbol symbol = table.getByQualifiedName(qualifiedName);
        if (symbol == null) return;

        for (CodeInheritance inh : allInh) {
            if (symbol.getId().equals(inh.getSubTypeId())) {
                String superRef = inh.getSuperTypeQualifiedName();
                CodeSymbol superSymbol = table.getById(superRef);
                String superQn = superSymbol != null ? superSymbol.getQualifiedName() : superRef;
                result.add(superQn);
                collectSuperHierarchy(superQn, table, allInh, visited, result, maxDepth - 1);
            }
        }
    }
}
