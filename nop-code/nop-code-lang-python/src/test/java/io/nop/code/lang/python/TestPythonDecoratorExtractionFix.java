package io.nop.code.lang.python;

import io.nop.code.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestPythonDecoratorExtractionFix {

    private PythonCodeFileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new PythonCodeFileAnalyzer();
    }

    @Test
    void testDataclassDecorator() {
        String source = "from dataclasses import dataclass\n\n@dataclass\nclass Point:\n    x: float\n    y: float\n";
        CodeFileAnalysisResult result = analyzer.analyze("point.py", source);
        assertNotNull(result);

        List<CodeAnnotationUsage> usages = result.getAnnotationUsages();
        boolean hasDataclass = usages.stream()
                .anyMatch(u -> "dataclass".equals(u.getAnnotationTypeQualifiedName()));
        assertTrue(hasDataclass, "Should extract @dataclass decorator");
    }

    @Test
    void testPytestFixtureDecorator() {
        String source = "import pytest\n\n@pytest.fixture\ndef my_fixture():\n    return 42\n";
        CodeFileAnalysisResult result = analyzer.analyze("conftest.py", source);
        assertNotNull(result);

        List<CodeAnnotationUsage> usages = result.getAnnotationUsages();
        boolean hasFixture = usages.stream()
                .anyMatch(u -> "pytest.fixture".equals(u.getAnnotationTypeQualifiedName()));
        assertTrue(hasFixture, "Should extract @pytest.fixture decorator");
    }

    @Test
    void testMultipleDecoratorsOnFunction() {
        String source = "import functools\n\n@functools.lru_cache(maxsize=128)\ndef expensive(x):\n    return x * x\n";
        CodeFileAnalysisResult result = analyzer.analyze("cached.py", source);
        assertNotNull(result);

        List<CodeAnnotationUsage> usages = result.getAnnotationUsages();
        assertFalse(usages.isEmpty(), "Should extract decorators");
    }
}
