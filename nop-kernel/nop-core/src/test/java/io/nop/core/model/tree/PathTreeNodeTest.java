package io.nop.core.model.tree;

import io.nop.commons.util.FileHelper;
import io.nop.commons.util.MavenDirHelper;
import io.nop.core.resource.path.PathTreeNode;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathTreeNodeTest extends BaseTestCase {
    private PathTreeNode root;
    private static final String TREE_TEXT =
            "项目名称\n" +
                    "    ├── 一级目录\n" +
                    "    │   ├── 文件1.txt\n" +
                    "    │   ├── 文件2.log\n" +
                    "    │   └── 二级目录\n" +
                    "    │       └── 文件3.dat\n" +
                    "    ├── 核心文件.py\n" +
                    "    └── 说明文档.md";

    @BeforeEach
    void setUp() {
        List<String> lines = Arrays.asList(TREE_TEXT.split("\n"));
        PathTreeNode.PathTreeParser parser = new PathTreeNode.PathTreeParser();
        root = parser.parse(lines);
        System.out.println(root);
    }

    @Test
    @DisplayName("测试正向解析-根节点")
    void testParseRoot() {
        assertEquals("项目名称", root.getName());
        assertTrue(root.isDirectory());
        assertEquals(0, root.getLevel());
        assertNull(root.getParent());
        assertEquals(3, root.getChildren().size());
    }

    @Test
    @DisplayName("测试正向解析-子节点结构")
    void testParseChildren() {
        PathTreeNode firstLevelDir = root.getChildren().get(0);
        assertEquals("一级目录", firstLevelDir.getName());
        assertTrue(firstLevelDir.isDirectory());
        assertEquals(1, firstLevelDir.getLevel());
        assertEquals(root, firstLevelDir.getParent());
        assertEquals(3, firstLevelDir.getChildren().size());

        PathTreeNode secondLevelFile = firstLevelDir.getChildren().get(0);
        assertEquals("文件1.txt", secondLevelFile.getName());
        assertFalse(secondLevelFile.isDirectory());
        assertEquals(2, secondLevelFile.getLevel());
        assertEquals(firstLevelDir, secondLevelFile.getParent());
    }

    @Test
    @DisplayName("测试反向生成-树结构文本")
    void testBuildTreeString() {
        String expected = "项目名称/\n" +
                "├── 一级目录/\n" +
                "│   ├── 文件1.txt\n" +
                "│   ├── 文件2.log\n" +
                "│   └── 二级目录/\n" +
                "│       └── 文件3.dat\n" +
                "├── 核心文件.py\n" +
                "└── 说明文档.md\n";

        assertEquals(expected, root.buildTreeString());
    }

    @Test
    @DisplayName("测试路径查找-存在节点")
    void testFindNodeExists() {
        PathTreeNode node = root.findNode("一级目录/二级目录/文件3.dat");
        assertNotNull(node);
        assertEquals("文件3.dat", node.getName());
        assertEquals("项目名称/一级目录/二级目录/文件3.dat", node.getFullPath());
    }

    @Test
    @DisplayName("测试路径查找-不存在节点")
    void testFindNodeNotExists() {
        PathTreeNode node = root.findNode("不存在的路径/文件.txt");
        assertNull(node);
    }

    @Test
    @DisplayName("测试文件扩展名提取")
    void testGetFileExtension() {
        PathTreeNode txtFile = root.findNode("一级目录/文件1.txt");
        PathTreeNode logFile = root.findNode("一级目录/文件2.log");
        PathTreeNode dir = root.findNode("一级目录/二级目录");

        assertEquals("txt", txtFile.getFileExtension());
        assertEquals("log", logFile.getFileExtension());
        assertEquals("", dir.getFileExtension());
    }

    @Test
    @DisplayName("测试获取所有文件")
    void testGetAllFiles() {
        List<PathTreeNode> files = root.getAllFiles();
        assertEquals(5, files.size());
        assertArrayEquals(
                new String[]{"文件1.txt", "文件2.log", "文件3.dat", "核心文件.py", "说明文档.md"},
                files.stream().map(PathTreeNode::getName).toArray()
        );
    }

    @Test
    @DisplayName("测试完整路径生成")
    void testGetFullPath() {
        assertEquals("项目名称/", root.getFullPath());
        assertEquals("项目名称/一级目录/", root.findNode("一级目录").getFullPath());
        assertEquals("项目名称/一级目录/文件1.txt", root.findNode("一级目录/文件1.txt").getFullPath());
        assertEquals("项目名称/一级目录/二级目录/文件3.dat", root.findNode("一级目录/二级目录/文件3.dat").getFullPath());
    }

    @Test
    @DisplayName("测试空输入解析")
    void testParseEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            new PathTreeNode.PathTreeParser().parse(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new PathTreeNode.PathTreeParser().parse(List.of());
        });
    }

    @Test
    @DisplayName("测试手动构建树结构")
    void testManualTreeConstruction() {
        PathTreeNode manualRoot = new PathTreeNode("手动项目", 0, null, true);
        PathTreeNode manualDir = new PathTreeNode("手动目录", 1, manualRoot, true);
        manualRoot.getChildren().add(manualDir);
        manualDir.getChildren().add(new PathTreeNode("手动文件.md", 2, manualDir, false));

        assertEquals("手动项目/\n└── 手动目录/\n    └── 手动文件.md\n",
                manualRoot.buildTreeString());
    }

    @Test
    @Disabled
    public void showPlatformModuleTree() {
        File dir = FileHelper.getAbsoluteFile(new File(getModuleDir(), "../.."));
        PathTreeNode node = PathTreeNode.createRootNode();
        genModuleNode(dir, node);
        System.out.println(node.buildTreeString());
    }

    void genModuleNode(File dir, PathTreeNode node){
        for(File subFile: dir.listFiles()){
            if(MavenDirHelper.isMavenModuleDir(subFile)){
                PathTreeNode child = node.addSubNode(subFile.getName());
                genModuleNode(subFile, child);
            }
        }
    }
}