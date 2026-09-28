package io.nop.code.lang.go;

import io.nop.treesitter.compat.TSNode;
import io.nop.treesitter.compat.TSParser;
import io.nop.treesitter.compat.TSTree;
import io.nop.treesitter.compat.TreeSitterGo;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class GoTreeDumpProbe {
    @Test
    public void dump() {
        String src = "package demo\n\nimport (\n\t\"fmt\"\n\tbase \"demo/base\"\n)\n\ntype Animal struct {\n\tName string\n\tbase.Base\n}\n";
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterGo());
        TSTree tree = parser.parseString(null, src);
        dump(tree.getRootNode(), src, 0);
    }

    private void dump(TSNode node, String src, int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append("  ");
        String text = src.substring(Math.min(node.getStartByte(), src.length()),
                Math.min(node.getEndByte(), src.length())).replace("\n", "\\n");
        if (text.length() > 30) text = text.substring(0, 30) + "...";
        sb.append(node.getType()).append(" [").append(node.getStartByte()).append(',').append(node.getEndByte()).append(") \"").append(text).append('"');
        System.out.println(sb);
        for (int i = 0; i < node.getChildCount(); i++) {
            dump(node.getChild(i), src, depth + 1);
        }
    }
}
