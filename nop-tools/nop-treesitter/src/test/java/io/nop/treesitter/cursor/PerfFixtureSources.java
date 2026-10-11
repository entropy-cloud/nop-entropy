package io.nop.treesitter.cursor;

/**
 * The six fixed per-grammar fixture sources shared by the allocation-reduction
 * equivalence tests and the render-golden dump. Each fixture exercises chain
 * containers (>8 children in one production), fields, and — where the grammar
 * has them — extras (comments) and hidden/aliased nodes.
 */
public final class PerfFixtureSources {

    private PerfFixtureSources() {
    }

    public static String json() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"keys\": [");
        for (int i = 0; i < 30; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"index\": ").append(i).append(", \"name\": \"item").append(i).append("\"}");
        }
        sb.append("], \"meta\": {");
        for (int i = 0; i < 12; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"field").append(i).append("\": ").append(i * 3);
        }
        sb.append("}}");
        return sb.toString();
    }

    public static String java() {
        StringBuilder sb = new StringBuilder();
        sb.append("public class Generated {\n");
        sb.append("    // header comment\n");
        for (int i = 0; i < 12; i++) {
            sb.append("    private int value").append(i).append(" = ").append(i).append(";\n");
        }
        sb.append("    /* block\n       comment */\n");
        sb.append("    public int sum(");
        for (int i = 0; i < 9; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("int arg").append(i);
        }
        sb.append(") {\n        return 0;\n    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static String javascript() {
        StringBuilder sb = new StringBuilder();
        sb.append("// leading comment\n");
        sb.append("const config = {");
        for (int i = 0; i < 12; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("option").append(i).append(": ").append(i);
        }
        sb.append("};\n");
        sb.append("function call() { return compute(1, 2, 3, 4, 5, 6, 7, 8, 9); }\n");
        return sb.toString();
    }

    public static String typescript() {
        StringBuilder sb = new StringBuilder();
        sb.append("// interface comment\n");
        sb.append("interface Shape {\n");
        for (int i = 0; i < 12; i++) {
            sb.append("    member").append(i).append(": string;\n");
        }
        sb.append("}\n");
        sb.append("type Union = ");
        for (int i = 0; i < 10; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append("'kind").append(i).append("'");
        }
        sb.append(";\n");
        return sb.toString();
    }

    public static String tsx() {
        StringBuilder sb = new StringBuilder();
        sb.append("// component\n");
        sb.append("const el = <div");
        for (int i = 0; i < 12; i++) {
            sb.append(" attr").append(i).append("=\"").append(i).append("\"");
        }
        sb.append(">content</div>;\n");
        return sb.toString();
    }

    public static String python() {
        StringBuilder sb = new StringBuilder();
        sb.append("# module comment\n");
        for (int i = 0; i < 12; i++) {
            sb.append("value").append(i).append(" = ").append(i).append("\n");
        }
        sb.append("def compute(");
        for (int i = 0; i < 9; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("arg").append(i);
        }
        sb.append("):\n    return None\n");
        return sb.toString();
    }
}
