package io.nop.code.lang.rust;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeInheritance;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.model.CodeRelationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("io.nop.code.lang.rust.TreeSitterNativeAvailableCondition#isNativeLibAvailable")
class TestRustCodeFileAnalyzer {

    private static final String RUST_SOURCE = """
            mod utils;

            use std::fmt;
            use crate::utils::helper;

            pub struct Animal {
                pub name: String,
            }

            pub enum Kind { A, B }

            pub trait Runner {
                fn run(&self) -> bool;
            }

            pub trait Super: Clone + Send {}

            pub type Alias = Animal;

            pub const MAX: i32 = 100;

            impl Animal {
                pub fn speak(&self) {
                    fmt::print("");
                    helper();
                }
            }

            impl Runner for Animal {
                fn run(&self) -> bool { true }
            }

            struct Pair<T: Clone> { v: T }

            impl<T: Clone> Super for Pair<T> {}
            """;

    private CodeFileAnalysisResult analyze() {
        RustCodeFileAnalyzer analyzer = new RustCodeFileAnalyzer();
        return analyzer.analyze("src/animal.rs", RUST_SOURCE);
    }

    @Test
    void symbolsExtractedWithKinds() {
        CodeFileAnalysisResult result = analyze();
        List<CodeSymbol> symbols = result.getSymbols();

        assertHasSymbol(symbols, CodeSymbolKind.CLASS, "Animal");
        assertHasSymbol(symbols, CodeSymbolKind.CLASS, "Kind");
        assertHasSymbol(symbols, CodeSymbolKind.INTERFACE, "Runner");
        assertHasSymbol(symbols, CodeSymbolKind.INTERFACE, "Super");
        assertHasSymbol(symbols, CodeSymbolKind.TYPE_ALIAS, "Alias");
        assertHasSymbol(symbols, CodeSymbolKind.CONSTANT, "MAX");

        // impl-in function_item → METHOD owned by Animal
        assertTrue(symbols.stream().anyMatch(s -> s.getKind() == CodeSymbolKind.METHOD
                        && "speak".equals(s.getName())
                        && "Animal.speak".equals(s.getQualifiedName())),
                "impl method must be owned by the self type: " + symbols);
        // trait signature → METHOD under Runner
        assertTrue(symbols.stream().anyMatch(s -> s.getKind() == CodeSymbolKind.METHOD
                        && "run".equals(s.getName())
                        && "Runner.run".equals(s.getQualifiedName())),
                "trait signature must be a Runner method: " + symbols);
        // field
        assertHasSymbol(symbols, CodeSymbolKind.FIELD, "name");
    }

    @Test
    void implTraitProducesImplementsEdgeChildToParent() {
        CodeFileAnalysisResult result = analyze();
        List<CodeInheritance> inheritances = result.getInheritances();
        String animalId = symbolId(result, "Animal");
        String pairId = symbolId(result, "Pair");

        assertTrue(inheritances.stream().anyMatch(i ->
                        animalId.equals(i.getSubTypeId())
                                && "Runner".equals(i.getSuperTypeQualifiedName())
                                && i.getRelationType() == CodeRelationType.IMPLEMENTS),
                "impl Runner for Animal must produce subType=Animal(symbol) -> superType=Runner: " + inheritances);
        assertTrue(inheritances.stream().anyMatch(i ->
                        pairId.equals(i.getSubTypeId())
                                && "Super".equals(i.getSuperTypeQualifiedName())
                                && i.getRelationType() == CodeRelationType.IMPLEMENTS),
                "generic impl must produce subType=Pair(symbol) -> superType=Super: " + inheritances);
    }

    private String symbolId(CodeFileAnalysisResult result, String name) {
        return result.getSymbols().stream()
                .filter(s -> name.equals(s.getName())
                        && (s.getKind() == CodeSymbolKind.CLASS || s.getKind() == CodeSymbolKind.INTERFACE))
                .map(CodeSymbol::getId)
                .findFirst()
                .orElse(name);
    }

    @Test
    void traitSupertraitProducesExtendsEdge() {
        CodeFileAnalysisResult result = analyze();
        String superId = result.getSymbols().stream()
                .filter(s -> "Super".equals(s.getName()) && s.getKind() == CodeSymbolKind.INTERFACE)
                .map(CodeSymbol::getId)
                .findFirst()
                .orElse("Super");
        assertTrue(result.getInheritances().stream().anyMatch(i ->
                        superId.equals(i.getSubTypeId())
                                && i.getRelationType() == CodeRelationType.EXTENDS
                                && ("Clone".equals(i.getSuperTypeQualifiedName())
                                    || "Send".equals(i.getSuperTypeQualifiedName()))),
                "trait supertrait bounds must produce EXTENDS edges: " + result.getInheritances());
    }

    @Test
    void structGenericBoundsDoNotProduceEdges() {
        CodeFileAnalysisResult result = analyze();
        // Pair<T: Clone> 的泛型参数 bounds 语义上非继承——不得出现 Pair→Clone EXTENDS
        assertTrue(result.getInheritances().stream().noneMatch(i ->
                        "Pair".equals(i.getSubTypeId())
                                && "Clone".equals(i.getSuperTypeQualifiedName())
                                && i.getRelationType() == CodeRelationType.EXTENDS),
                "struct generic bounds must not produce EXTENDS edges: " + result.getInheritances());
    }

    @Test
    void importsAndCallsExtracted() {
        CodeFileAnalysisResult result = analyze();
        assertTrue(result.getImports().contains("std::fmt"));
        assertTrue(result.getImports().contains("crate::utils::helper"));

        List<CodeMethodCall> calls = result.getCalls();
        assertTrue(calls.stream().anyMatch(c -> "print".equals(c.getMethodName())
                        && "fmt::print".equals(c.getCalleeQualifiedName())),
                "fmt::print scoped call must be extracted: " + calls);
        assertTrue(calls.stream().anyMatch(c -> "helper".equals(c.getMethodName())
                        && "helper".equals(c.getCalleeQualifiedName())),
                "same-module helper call must resolve: " + calls);
    }

    @Test
    void blankInputReturnsNull() {
        RustCodeFileAnalyzer analyzer = new RustCodeFileAnalyzer();
        assertNull(analyzer.analyze("a.rs", null));
        assertNull(analyzer.analyze("a.rs", "   "));
    }

    private void assertHasSymbol(List<CodeSymbol> symbols, CodeSymbolKind kind, String name) {
        assertTrue(symbols.stream().anyMatch(s -> s.getKind() == kind && name.equals(s.getName())),
                "missing symbol " + kind + " " + name + ": " + symbols);
    }
}
