package io.nop.code.lang.go;

import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeInheritance;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("io.nop.code.lang.go.TreeSitterNativeAvailableCondition#isNativeLibAvailable")
class TestGoCodeFileAnalyzer {

    private static final String GO_SOURCE = """
            package demo

            import (
            	"fmt"
            	base "demo/base"
            )

            type Animal struct {
            	Name string
            	base.Base
            }

            type Runner interface {
            	base.Mover
            	Run(speed int) error
            }

            type Alias = Animal

            const MaxSpeed = 100

            func NewAnimal(name string) *Animal {
            	return &Animal{Name: name}
            }

            func (a *Animal) Speak() {
            	fmt.Println(a.Name)
            	NewAnimal("x")
            }

            func (r *Runner) Run(speed int) error {
            	return nil
            }
            """;

    private CodeFileAnalysisResult analyze() {
        GoCodeFileAnalyzer analyzer = new GoCodeFileAnalyzer();
        return analyzer.analyze("demo/animal.go", GO_SOURCE);
    }

    @Test
    void packageAndImportsCollected() {
        CodeFileAnalysisResult result = analyze();
        assertNotNull(result);
        assertEquals("demo", result.getPackageName());
        assertTrue(result.getImports().contains("fmt"));
        assertTrue(result.getImports().contains("demo/base"));
    }

    @Test
    void symbolsExtractedWithKindsAndQn() {
        CodeFileAnalysisResult result = analyze();
        List<CodeSymbol> symbols = result.getSymbols();

        assertHasSymbol(symbols, CodeSymbolKind.CLASS, "Animal", "demo.Animal");
        assertHasSymbol(symbols, CodeSymbolKind.INTERFACE, "Runner", "demo.Runner");
        assertHasSymbol(symbols, CodeSymbolKind.TYPE_ALIAS, "Alias", "demo.Alias");
        assertHasSymbol(symbols, CodeSymbolKind.CONSTANT, "MaxSpeed", "demo.MaxSpeed");
        assertHasSymbol(symbols, CodeSymbolKind.FUNCTION, "NewAnimal", "demo.NewAnimal");

        // method: name from field_identifier, qn = receiverType.method
        assertHasSymbol(symbols, CodeSymbolKind.METHOD, "Speak", "demo.Animal.Speak");
        assertHasSymbol(symbols, CodeSymbolKind.METHOD, "Run", "demo.Runner.Run");
    }

    @Test
    void structAndInterfaceEmbedsProduceInheritance() {
        CodeFileAnalysisResult result = analyze();
        List<CodeInheritance> inheritances = result.getInheritances();

        assertTrue(inheritances.stream().anyMatch(i -> "base.Base".equals(i.getSuperTypeQualifiedName())),
                "struct embed base.Base must produce an inheritance edge with the qualified text: " + inheritances);
        assertTrue(inheritances.stream().anyMatch(i -> "demo.base.Mover".equals(i.getSuperTypeQualifiedName())
                        || "base.Mover".equals(i.getSuperTypeQualifiedName())),
                "interface embed base.Mover must produce an inheritance edge: " + inheritances);
        assertEquals(2, inheritances.size());
    }

    @Test
    void callsExtracted() {
        CodeFileAnalysisResult result = analyze();
        List<CodeMethodCall> calls = result.getCalls();
        assertTrue(calls.stream().anyMatch(c -> "Println".equals(c.getMethodName())),
                "fmt.Println call must be extracted: " + calls);
        assertTrue(calls.stream().anyMatch(c -> "NewAnimal".equals(c.getMethodName())
                        && "demo.NewAnimal".equals(c.getCalleeQualifiedName())),
                "same-package call must resolve to the declared symbol qn: " + calls);
    }

    @Test
    void namedFieldsAreFieldSymbols() {
        CodeFileAnalysisResult result = analyze();
        List<CodeSymbol> symbols = result.getSymbols();
        assertHasSymbol(symbols, CodeSymbolKind.FIELD, "Name", "demo.Animal.Name");
    }

    @Test
    void blankInputReturnsNull() {
        GoCodeFileAnalyzer analyzer = new GoCodeFileAnalyzer();
        assertNull(analyzer.analyze("a.go", null));
        assertNull(analyzer.analyze("a.go", "   "));
    }

    private void assertHasSymbol(List<CodeSymbol> symbols, CodeSymbolKind kind, String name, String qn) {
        assertTrue(symbols.stream().anyMatch(s -> s.getKind() == kind && name.equals(s.getName())
                        && qn.equals(s.getQualifiedName())),
                "missing symbol " + kind + " " + name + " (" + qn + "): " + symbols);
    }
}
