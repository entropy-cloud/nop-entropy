package io.nop.ai.code_analyzer.maven;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MavenDependencyTreeParserTest {

    @Test
    void parseSingleDependencyTree() {
        List<String> input = List.of("org.example:parent:jar:1.0.0");
        
        MavenDependencyNode root =
            MavenDependencyTreeParser.parse(input);
        
        assertEquals("org.example:parent:jar:1.0.0", 
            root.getDependency().toString());
        assertTrue(root.getChildren().isEmpty());
    }

    @Test
    void parseSimpleTreeWithChildren() {
        List<String> input = Arrays.asList(
            "org.example:parent:jar:1.0.0",
            "+- org.example:child1:jar:1.0.0:compile",
            "\\- org.example:child2:jar:1.0.0:runtime"
        );
        
        MavenDependencyNode root =
            MavenDependencyTreeParser.parse(input);
        
        assertEquals(2, root.getChildren().size());
        assertEquals("org.example:child1:jar:1.0.0:compile", 
            root.getChildren().get(0).getDependency().toString());
        assertEquals("org.example:child2:jar:1.0.0:runtime", 
            root.getChildren().get(1).getDependency().toString());
    }

    @Test
    void parseMultiLevelTree() {
        List<String> input = Arrays.asList(
            "org.example:root:jar:1.0.0",
            "+- org.example:level1:jar:1.0.0",
            "|  +- org.example:level2a:jar:1.0.0",
            "|  \\- org.example:level2b:jar:1.0.0",
            "\\- org.example:level1b:jar:1.0.0"
        );
        
        MavenDependencyNode root =
            MavenDependencyTreeParser.parse(input);
        
        assertEquals(2, root.getChildren().size());
        assertEquals(2, root.getChildren().get(0).getChildren().size());
        assertEquals("org.example:level2a:jar:1.0.0", 
            root.getChildren().get(0).getChildren().get(0).getDependency().toString());
    }

    @Test
    void parseEmptyInputShouldThrow() {
        assertThrows(IllegalArgumentException.class, 
            () -> MavenDependencyTreeParser.parse(List.of()));
    }

    @Test
    void parseMalformedLineShouldThrow() {
        List<String> input = Arrays.asList(
            "org.example:good:jar:1.0.0",
            "+- invalid-dependency-format"
        );
        
        assertThrows(IllegalArgumentException.class, 
            () -> MavenDependencyTreeParser.parse(input));
    }
}