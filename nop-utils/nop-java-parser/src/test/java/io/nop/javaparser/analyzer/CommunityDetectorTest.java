package io.nop.javaparser.analyzer;

import io.nop.code.core.analyzer.CommunityDetector;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommunityDetector 单元测试
 */
class CommunityDetectorTest {
    
    @Test
    @DisplayName("空调用图返回空结果")
    void testEmptyCallGraph() {
        CallGraph callGraph = new CallGraph();
        SymbolTable symbolTable = new SymbolTable();
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable);
        
        assertNotNull(result);
        assertEquals(0, result.getTotalSymbols());
        assertEquals(0, result.getTotalCommunities());
        assertTrue(result.getCommunities().isEmpty());
    }
    
    @Test
    @DisplayName("单个孤立节点返回空社区")
    void testSingleIsolatedNode() {
        // CallGraph only tracks nodes via edges; isolated nodes produce 0 totalSymbols
        CallGraph callGraph = new CallGraph();
        SymbolTable symbolTable = new SymbolTable();
        CodeSymbol symbol = new CodeSymbol();
        symbol.setId("com.example.Service");
        symbol.setQualifiedName("com.example.Service.method");
        symbol.setName("method");
        symbol.setKind(CodeSymbolKind.METHOD);
        symbolTable.add(symbol);
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable);
        
        assertNotNull(result);
        // 孤立节点不形成社区
        assertEquals(0, result.getTotalCommunities());
    }
    
    @Test
    @DisplayName("简单链式调用形成社区")
    void testSimpleChain() {
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "C");
        
        SymbolTable symbolTable = createSymbolTable("A", "B", "C");
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable);
        
        assertNotNull(result);
        assertEquals(3, result.getTotalSymbols());
        assertTrue(result.getTotalCommunities() >= 1);
        assertTrue(result.getClusteredSymbols() >= 2);
    }
    
    @Test
    @DisplayName("环形调用形成社区")
    void testCyclicCalls() {
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "C");
        callGraph.addEdge("C", "A");
        
        SymbolTable symbolTable = createSymbolTable("A", "B", "C");
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable);
        
        assertNotNull(result);
        assertEquals(3, result.getTotalSymbols());
        assertEquals(1, result.getTotalCommunities());
        assertEquals(3, result.getClusteredSymbols());
    }
    
    @Test
    @DisplayName("两个独立社区")
    void testTwoIndependentCommunities() {
        CallGraph callGraph = new CallGraph();
        // 社区 1
        callGraph.addEdge("A1", "A2");
        callGraph.addEdge("A2", "A3");
        // 社区 2
        callGraph.addEdge("B1", "B2");
        callGraph.addEdge("B2", "B3");
        
        SymbolTable symbolTable = createSymbolTable("A1", "A2", "A3", "B1", "B2", "B3");
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable);
        
        assertNotNull(result);
        assertEquals(6, result.getTotalSymbols());
        // 应该检测到 2 个社区
        assertEquals(2, result.getTotalCommunities());
        assertEquals(6, result.getClusteredSymbols());
    }
    
    @Test
    @DisplayName("大图模式自动启用")
    void testLargeGraphModeEnabled() {
        // 创建 15,000 个节点 (超过默认阈值 10,000)
        CallGraph callGraph = new CallGraph();
        SymbolTable symbolTable = new SymbolTable();
        
        for (int i = 0; i < 15000; i++) {
            String nodeId = "Node" + i;
            // 每个节点调用下一个节点，形成链
            String nextNodeId = "Node" + ((i + 1) % 15000);
            callGraph.addEdge(nodeId, nextNodeId);
            
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(nodeId);
            symbol.setQualifiedName("com.example.pkg" + (i / 1000) + "." + nodeId);
            symbol.setName(nodeId);
            symbol.setKind(CodeSymbolKind.METHOD);
            symbolTable.add(symbol);
        }
        
        // 配置较低阈值以快速测试
        CommunityDetector.CommunityConfig config = new CommunityDetector.CommunityConfig();
        config.setLargeGraphThreshold(1000);  // 降低阈值
        config.setMinNodeDegree(1);           // 不过滤节点
        config.setLargeGraphMaxIterations(2); // 减少迭代
        config.setEnableTimeout(true);
        config.setTimeoutMs(30000);           // 30秒超时
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable, config);
        
        assertNotNull(result);
        assertTrue(result.isLargeGraphMode(), "应该启用大图模式");
        assertTrue(result.getTotalSymbols() >= 1000);
        assertTrue(result.getProcessingTimeMs() >= 0);
        
        System.out.println(CommunityDetector.printSummary(result));
    }
    
    @Test
    @DisplayName("大图模式过滤低度数节点")
    void testLargeGraphFiltersLowDegreeNodes() {
        CallGraph callGraph = new CallGraph();
        SymbolTable symbolTable = new SymbolTable();
        
        // 创建核心社区 (高度数节点)
        for (int i = 0; i < 100; i++) {
            String nodeId = "Core" + i;
            for (int j = 0; j < 100; j++) {
                if (i != j) {
                    callGraph.addEdge(nodeId, "Core" + j);
                }
            }
            
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(nodeId);
            symbol.setQualifiedName("com.example.core." + nodeId);
            symbol.setName(nodeId);
            symbol.setKind(CodeSymbolKind.METHOD);
            symbolTable.add(symbol);
        }
        
        // 添加噪音节点 (度数为 1)
        for (int i = 0; i < 500; i++) {
            String noiseId = "Noise" + i;
            callGraph.addEdge(noiseId, "Core0");
            
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(noiseId);
            symbol.setQualifiedName("com.example.noise." + noiseId);
            symbol.setName(noiseId);
            symbol.setKind(CodeSymbolKind.METHOD);
            symbolTable.add(symbol);
        }
        
        // 启用大图模式，过滤度数 < 2 的节点
        CommunityDetector.CommunityConfig config = new CommunityDetector.CommunityConfig();
        config.setLargeGraphThreshold(100);
        config.setMinNodeDegree(2);
        config.setEnableTimeout(false);
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable, config);
        
        assertNotNull(result);
        assertTrue(result.isLargeGraphMode());
        assertTrue(result.getFilteredNodes() >= 500, "应该过滤掉噪音节点");
        assertTrue(result.getNodesProcessed() <= 100, "只处理核心节点");
        
        System.out.println("Filtered nodes: " + result.getFilteredNodes());
        System.out.println("Nodes processed: " + result.getNodesProcessed());
    }
    
    @Test
    @DisplayName("自定义配置测试")
    void testCustomConfig() {
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "C");
        callGraph.addEdge("C", "D");
        
        SymbolTable symbolTable = createSymbolTable("A", "B", "C", "D");
        
        CommunityDetector.CommunityConfig config = new CommunityDetector.CommunityConfig();
        config.setMinCommunitySize(3);  // 只保留 3+ 节点的社区
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable, config);
        
        assertNotNull(result);
    }
    
    @Test
    @DisplayName("内聚度计算正确性")
    void testCohesionCalculation() {
        // 完全连接的社区 (内聚度 = 1.0)
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("A", "C");
        callGraph.addEdge("B", "A");
        callGraph.addEdge("B", "C");
        callGraph.addEdge("C", "A");
        callGraph.addEdge("C", "B");
        
        SymbolTable symbolTable = createSymbolTable("A", "B", "C");
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable);
        
        assertNotNull(result);
        assertFalse(result.getCommunities().isEmpty());
        
        CommunityDetector.Community community = result.getCommunities().get(0);
        assertTrue(community.getCohesion() > 0.5, "高内聚社区应该有高内聚度");
    }
    
    @Test
    @DisplayName("社区标签生成测试")
    void testLabelGeneration() {
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "C");
        
        SymbolTable symbolTable = new SymbolTable();
        
        CodeSymbol symbolA = new CodeSymbol();
        symbolA.setId("A");
        symbolA.setQualifiedName("com.example.service.UserService.methodA");
        symbolA.setName("methodA");
        symbolA.setKind(CodeSymbolKind.METHOD);
        symbolTable.add(symbolA);
        
        CodeSymbol symbolB = new CodeSymbol();
        symbolB.setId("B");
        symbolB.setQualifiedName("com.example.service.UserService.methodB");
        symbolB.setName("methodB");
        symbolB.setKind(CodeSymbolKind.METHOD);
        symbolTable.add(symbolB);
        
        CodeSymbol symbolC = new CodeSymbol();
        symbolC.setId("C");
        symbolC.setQualifiedName("com.example.service.UserService.methodC");
        symbolC.setName("methodC");
        symbolC.setKind(CodeSymbolKind.METHOD);
        symbolTable.add(symbolC);
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable);
        
        assertNotNull(result);
        assertFalse(result.getCommunities().isEmpty());
        
        CommunityDetector.Community community = result.getCommunities().get(0);
        assertNotNull(community.getLabel());
        assertNotNull(community.getDominantPackage());
    }
    
    @Test
    @DisplayName("打印摘要测试")
    void testPrintSummary() {
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "C");
        
        SymbolTable symbolTable = createSymbolTable("A", "B", "C");
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable);
        
        String summary = CommunityDetector.printSummary(result);
        
        assertNotNull(summary);
        assertTrue(summary.contains("Community Detection Summary"));
        assertTrue(summary.contains("Total symbols"));
        assertTrue(summary.contains("Communities found"));
    }
    
    @Test
    @DisplayName("获取符号所属社区")
    void testGetCommunityForSymbol() {
        CallGraph callGraph = new CallGraph();
        callGraph.addEdge("A", "B");
        callGraph.addEdge("B", "C");
        
        SymbolTable symbolTable = createSymbolTable("A", "B", "C");
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable);
        
        if (!result.getCommunities().isEmpty()) {
            CommunityDetector.Community community = result.getCommunities().get(0);
            String symbolId = community.getSymbolIds().get(0);
            
            CommunityDetector.Community found = 
                    CommunityDetector.getCommunityForSymbol(symbolId, result.getCommunities());
            
            assertNotNull(found);
            assertEquals(community.getId(), found.getId());
        }
        
        // 查找不存在的符号
        CommunityDetector.Community notFound = 
                CommunityDetector.getCommunityForSymbol("NonExistent", result.getCommunities());
        assertNull(notFound);
    }
    
    // ==================== 辅助方法 ====================
    
    private SymbolTable createSymbolTable(String... ids) {
        SymbolTable symbolTable = new SymbolTable();
        for (String id : ids) {
            CodeSymbol symbol = new CodeSymbol();
            symbol.setId(id);
            symbol.setQualifiedName("com.example." + id);
            symbol.setName(id);
            symbol.setKind(CodeSymbolKind.METHOD);
            symbolTable.add(symbol);
        }
        return symbolTable;
    }
}
