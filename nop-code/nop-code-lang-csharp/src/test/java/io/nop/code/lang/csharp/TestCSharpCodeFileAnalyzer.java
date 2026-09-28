package io.nop.code.lang.csharp;

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

@EnabledIf("io.nop.code.lang.csharp.TreeSitterNativeAvailableCondition#isNativeLibAvailable")
class TestCSharpCodeFileAnalyzer {

    private static final String CS_SOURCE = """
            namespace Demo.Animals {
                using System;

                public interface IRunner {
                    bool Run(int speed);
                }

                public class Animal : IRunner {
                    private const int Max = 100;
                    public string Name { get; set; }
                    public Animal Mother { get; set; }
                    public bool Run(int speed) {
                        Console.WriteLine(Name);
                        return true;
                    }
                }

                public class Dog : Animal {}
            }
            """;

    private CodeFileAnalysisResult analyze() {
        CSharpCodeFileAnalyzer analyzer = new CSharpCodeFileAnalyzer();
        return analyzer.analyze("Animals/Animal.cs", CS_SOURCE);
    }

    @Test
    void fileScopedAndBlockNamespacePrefixes() {
        CSharpCodeFileAnalyzer analyzer = new CSharpCodeFileAnalyzer();
        CodeFileAnalysisResult result = analyzer.analyze("a.cs",
                "namespace Demo.Birds;\n\npublic class Bird {}\n");
        assertNotNull(result);
        assertTrue(result.getSymbols().stream().anyMatch(s ->
                        "Bird".equals(s.getName()) && "Demo.Birds.Bird".equals(s.getQualifiedName())),
                "file-scoped namespace with qualified_name must prefix the qn: " + result.getSymbols());
    }

    @Test
    void symbolsExtractedWithNamespaceQn() {
        CodeFileAnalysisResult result = analyze();
        List<CodeSymbol> symbols = result.getSymbols();

        assertHasSymbol(symbols, CodeSymbolKind.INTERFACE, "IRunner", "Demo.Animals.IRunner");
        assertHasSymbol(symbols, CodeSymbolKind.CLASS, "Animal", "Demo.Animals.Animal");
        assertHasSymbol(symbols, CodeSymbolKind.CLASS, "Dog", "Demo.Animals.Dog");
        assertHasSymbol(symbols, CodeSymbolKind.FIELD, "Name", "Demo.Animals.Animal.Name");
        assertHasSymbol(symbols, CodeSymbolKind.CONSTANT, "Max", "Demo.Animals.Animal.Max");
        // method inside class → METHOD with class qn
        assertHasSymbol(symbols, CodeSymbolKind.METHOD, "Run", "Demo.Animals.Animal.Run");
        // interface declaration method → METHOD with interface qn
        assertHasSymbol(symbols, CodeSymbolKind.METHOD, "Run", "Demo.Animals.IRunner.Run");
    }

    @Test
    void customTypePropertyNamedAfterPropertyName() {
        CodeFileAnalysisResult result = analyze();
        // public Animal Mother { get; set; } — the first identifier is the TYPE
        assertHasSymbol(result.getSymbols(), CodeSymbolKind.FIELD, "Mother",
                "Demo.Animals.Animal.Mother");
        // and there must be no bogus "Animal" FIELD under the class
        assertTrue(result.getSymbols().stream().noneMatch(s ->
                        s.getKind() == CodeSymbolKind.FIELD && "Animal".equals(s.getName())),
                "the property type identifier must not be taken as the name");
    }

    @Test
    void baseListEdgesByContainer() {
        CodeFileAnalysisResult result = analyze();
        List<CodeInheritance> inheritances = result.getInheritances();
        String animalId = idOf(result, "Animal", CodeSymbolKind.CLASS);

        // class : interface → IMPLEMENTS (child→parent)
        assertTrue(inheritances.stream().anyMatch(i ->
                        animalId.equals(i.getSubTypeId())
                                && "Demo.Animals.IRunner".equals(i.getSuperTypeQualifiedName())
                                && i.getRelationType() == CodeRelationType.IMPLEMENTS),
                "class base interface must produce IMPLEMENTS: " + inheritances);
        // class : class → EXTENDS
        String dogId = idOf(result, "Dog", CodeSymbolKind.CLASS);
        assertTrue(inheritances.stream().anyMatch(i ->
                        dogId.equals(i.getSubTypeId())
                                && "Demo.Animals.Animal".equals(i.getSuperTypeQualifiedName())
                                && i.getRelationType() == CodeRelationType.EXTENDS),
                "class base class must produce EXTENDS: " + inheritances);
    }

    @Test
    void structBasesAllImplements() {
        CSharpCodeFileAnalyzer analyzer = new CSharpCodeFileAnalyzer();
        CodeFileAnalysisResult result = analyzer.analyze("s.cs",
                "namespace Demo {\n    public struct P : System.IComparable {}\n}\n");
        String pid = idOf(result, "P", CodeSymbolKind.CLASS);
        assertTrue(result.getInheritances().stream().anyMatch(i ->
                        pid.equals(i.getSubTypeId())
                                && i.getRelationType() == CodeRelationType.IMPLEMENTS),
                "struct bases must all be IMPLEMENTS: " + result.getInheritances());
    }

    @Test
    void interfaceBasesAllExtends() {
        CSharpCodeFileAnalyzer analyzer = new CSharpCodeFileAnalyzer();
        CodeFileAnalysisResult result = analyzer.analyze("i.cs",
                "namespace Demo {\n    public interface ISub : IBase {}\n    public interface IBase {}\n}\n");
        String isubId = idOf(result, "ISub", CodeSymbolKind.INTERFACE);
        assertTrue(result.getInheritances().stream().anyMatch(i ->
                        isubId.equals(i.getSubTypeId())
                                && i.getRelationType() == CodeRelationType.EXTENDS),
                "interface bases must all be EXTENDS: " + result.getInheritances());
    }

    @Test
    void callsExtractedFromMethodBody() {
        CodeFileAnalysisResult result = analyze();
        List<CodeMethodCall> calls = result.getCalls();
        assertTrue(calls.stream().anyMatch(c -> "WriteLine".equals(c.getMethodName())
                        && "Console.WriteLine".equals(c.getCalleeQualifiedName())),
                "Console.WriteLine invocation must be extracted: " + calls);
    }

    @Test
    void usingCollected() {
        CodeFileAnalysisResult result = analyze();
        assertTrue(result.getImports().contains("System"));
    }

    @Test
    void packageNameSyncedFromNamespaceStack() {
        CodeFileAnalysisResult result = analyze();
        assertEquals("Demo.Animals", result.getPackageName());
    }

    @Test
    void blankInputReturnsNull() {
        CSharpCodeFileAnalyzer analyzer = new CSharpCodeFileAnalyzer();
        assertNull(analyzer.analyze("a.cs", null));
        assertNull(analyzer.analyze("a.cs", "   "));
    }

    private String idOf(CodeFileAnalysisResult result, String name, CodeSymbolKind kind) {
        return result.getSymbols().stream()
                .filter(s -> name.equals(s.getName()) && s.getKind() == kind)
                .map(CodeSymbol::getId)
                .findFirst()
                .orElse(name);
    }

    private void assertHasSymbol(List<CodeSymbol> symbols, CodeSymbolKind kind, String name, String qn) {
        assertTrue(symbols.stream().anyMatch(s -> s.getKind() == kind && name.equals(s.getName())
                        && qn.equals(s.getQualifiedName())),
                "missing symbol " + kind + " " + name + " (" + qn + "): " + symbols);
    }
}
