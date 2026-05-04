package io.nop.code.lang.typescript;

import io.nop.code.core.model.*;
import io.nop.code.lang.typescript.analyzer.TypeScriptCodeFileAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestTypeScriptCodeFileAnalyzer {

    private TypeScriptCodeFileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new TypeScriptCodeFileAnalyzer();
    }

    private static final String CLASS_SOURCE =
            "@Injectable()\n" +
            "export class UserService extends BaseService implements IUserService {\n" +
            "  private name: string;\n" +
            "  public getName(): string { return this.name; }\n" +
            "  static create(): UserService { return new UserService(); }\n" +
            "}\n";

    private static final String INTERFACE_SOURCE =
            "export interface IUserService {\n" +
            "  getName(): string;\n" +
            "  readonly id: number;\n" +
            "}\n";

    private static final String ENUM_SOURCE =
            "export enum Status {\n" +
            "  Active = 'active',\n" +
            "  Inactive = 'inactive'\n" +
            "}\n";

    private static final String FUNCTION_SOURCE =
            "export async function fetchData(url: string): Promise<void> {\n" +
            "  const response = await fetch(url);\n" +
            "  return response.json();\n" +
            "}\n";

    @Test
    void testAnalyzeClass() {
        CodeFileAnalysisResult result = analyzer.analyze("src/service/UserService.ts", CLASS_SOURCE);
        assertNotNull(result);
        assertEquals(CodeLanguage.TYPESCRIPT, result.getLanguage());

        CodeSymbol cls = findSymbol(result.getSymbols(), "UserService");
        assertNotNull(cls, "Should find UserService symbol");
        assertEquals(CodeSymbolKind.CLASS, cls.getKind());
        assertTrue(cls.getQualifiedName().contains("UserService"));
        assertEquals(2, cls.getLine());
    }

    @Test
    void testInheritances() {
        CodeFileAnalysisResult result = analyzer.analyze("src/service/UserService.ts", CLASS_SOURCE);
        assertNotNull(result);

        List<CodeInheritance> inheritances = result.getInheritances();
        boolean hasExtends = inheritances.stream()
                .anyMatch(i -> "BaseService".equals(i.getSuperTypeQualifiedName())
                        && i.getRelationType() == CodeRelationType.EXTENDS);
        boolean hasImplements = inheritances.stream()
                .anyMatch(i -> "IUserService".equals(i.getSuperTypeQualifiedName())
                        && i.getRelationType() == CodeRelationType.IMPLEMENTS);

        assertTrue(hasExtends, "Should have EXTENDS BaseService");
        assertTrue(hasImplements, "Should have IMPLEMENTS IUserService");
    }

    @Test
    void testClassMembers() {
        CodeFileAnalysisResult result = analyzer.analyze("src/service/UserService.ts", CLASS_SOURCE);
        assertNotNull(result);

        CodeSymbol nameField = findSymbol(result.getSymbols(), "name");
        assertNotNull(nameField, "Should find name field");
        assertEquals(CodeSymbolKind.FIELD, nameField.getKind());
        assertEquals(CodeAccessModifier.PRIVATE, nameField.getAccessModifier());

        CodeSymbol getNameMethod = findSymbol(result.getSymbols(), "getName");
        assertNotNull(getNameMethod, "Should find getName method");
        assertEquals(CodeSymbolKind.METHOD, getNameMethod.getKind());
        assertEquals(CodeAccessModifier.PUBLIC, getNameMethod.getAccessModifier());

        CodeSymbol createMethod = findSymbol(result.getSymbols(), "create");
        assertNotNull(createMethod, "Should find create method");
        assertTrue(createMethod.isStaticFlag(), "create should be static");
    }

    @Test
    void testDecorators() {
        CodeFileAnalysisResult result = analyzer.analyze("src/service/UserService.ts", CLASS_SOURCE);
        assertNotNull(result);

        List<CodeAnnotationUsage> annotations = result.getAnnotationUsages();
        boolean hasInjectable = annotations.stream()
                .anyMatch(a -> "Injectable".equals(a.getAnnotationTypeQualifiedName()));
        assertTrue(hasInjectable, "Should have @Injectable decorator");
    }

    @Test
    void testInterface() {
        CodeFileAnalysisResult result = analyzer.analyze("src/types/IUserService.ts", INTERFACE_SOURCE);
        assertNotNull(result);

        CodeSymbol iface = findSymbol(result.getSymbols(), "IUserService");
        assertNotNull(iface, "Should find IUserService symbol");
        assertEquals(CodeSymbolKind.INTERFACE, iface.getKind());

        CodeSymbol idField = findSymbol(result.getSymbols(), "id");
        assertNotNull(idField, "Should find id property");
        assertEquals(CodeSymbolKind.FIELD, idField.getKind());
        assertTrue(idField.isReadonlyFlag(), "id should be readonly");

        CodeSymbol getNameMethod = findSymbol(result.getSymbols(), "getName");
        assertNotNull(getNameMethod, "Should find getName method");
        assertEquals(CodeSymbolKind.METHOD, getNameMethod.getKind());
    }

    @Test
    void testEnum() {
        CodeFileAnalysisResult result = analyzer.analyze("src/types/Status.ts", ENUM_SOURCE);
        assertNotNull(result);

        CodeSymbol enumSymbol = findSymbol(result.getSymbols(), "Status");
        assertNotNull(enumSymbol, "Should find Status symbol");
        assertEquals(CodeSymbolKind.ENUM, enumSymbol.getKind());
    }

    @Test
    void testFunction() {
        CodeFileAnalysisResult result = analyzer.analyze("src/api/fetchData.ts", FUNCTION_SOURCE);
        assertNotNull(result);

        CodeSymbol func = findSymbol(result.getSymbols(), "fetchData");
        assertNotNull(func, "Should find fetchData symbol");
        assertEquals(CodeSymbolKind.FUNCTION, func.getKind());
        assertTrue(func.isAsyncFlag(), "fetchData should be async");
    }

    @Test
    void testTsxUsesTsxGrammar() {
        String tsxSource =
                "export const App = () => {\n" +
                "  return <div>Hello</div>;\n" +
                "};\n";

        CodeFileAnalysisResult result = analyzer.analyze("src/App.tsx", tsxSource);
        assertNotNull(result, "TSX should parse successfully");
        assertEquals(CodeLanguage.TYPESCRIPT, result.getLanguage());
    }

    @Test
    void testEmptySourceReturnsNull() {
        assertNull(analyzer.analyze("Empty.ts", ""));
        assertNull(analyzer.analyze("Empty.ts", "   "));
        assertNull(analyzer.analyze("Empty.ts", null));
    }

    private CodeSymbol findSymbol(List<CodeSymbol> symbols, String name) {
        return symbols.stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElse(null);
    }
}
