package io.nop.javaparser.analyzer;

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
        Map<String, List<String>> callGraph = new HashMap<>();
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        
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
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("com.example.Service", Collections.emptyList());
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        SymbolInfo symbol = new SymbolInfo();
        symbol.setId("com.example.Service");
        symbol.setQualifiedName("com.example.Service.method");
        symbol.setName("method");
        symbolTable.put("com.example.Service", symbol);
        
        CommunityDetector.CommunityDetectionResult result = 
                CommunityDetector.detectCommunities(callGraph, symbolTable);
        
        assertNotNull(result);
        // 孤立节点不形成社区
        assertEquals(0, result.getTotalCommunities());
    }
    
    @Test
    @DisplayName("简单链式调用形成社区")
    void testSimpleChain() {
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("A", List.of("B"));
        callGraph.put("B", List.of("C"));
        callGraph.put("C", Collections.emptyList());
        
        Map<String, SymbolInfo> symbolTable = createSymbolTable("A", "B", "C");
        
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
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("A", List.of("B"));
        callGraph.put("B", List.of("C"));
        callGraph.put("C", List.of("A"));
        
        Map<String, SymbolInfo> symbolTable = createSymbolTable("A", "B", "C");
        
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
        Map<String, List<String>> callGraph = new HashMap<>();
        // 社区 1
        callGraph.put("A1", List.of("A2"));
        callGraph.put("A2", List.of("A3"));
        callGraph.put("A3", Collections.emptyList());
        // 社区 2
        callGraph.put("B1", List.of("B2"));
        callGraph.put("B2", List.of("B3"));
        callGraph.put("B3", Collections.emptyList());
        
        Map<String, SymbolInfo> symbolTable = createSymbolTable("A1", "A2", "A3", "B1", "B2", "B3");
        
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
        Map<String, List<String>> callGraph = new HashMap<>();
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        
        for (int i = 0; i < 15000; i++) {
            String nodeId = "Node" + i;
            // 每个节点调用下一个节点，形成链
            String nextNodeId = "Node" + ((i + 1) % 15000);
            callGraph.put(nodeId, List.of(nextNodeId));
            
            SymbolInfo symbol = new SymbolInfo();
            symbol.setId(nodeId);
            symbol.setQualifiedName("com.example.pkg" + (i / 1000) + "." + nodeId);
            symbol.setName(nodeId);
            symbolTable.put(nodeId, symbol);
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
        assertNotNull(result.getProcessingTimeMs());
        
        System.out.println(CommunityDetector.printSummary(result));
    }
    
    @Test
    @DisplayName("大图模式过滤低度数节点")
    void testLargeGraphFiltersLowDegreeNodes() {
        Map<String, List<String>> callGraph = new HashMap<>();
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        
        // 创建核心社区 (高度数节点)
        for (int i = 0; i < 100; i++) {
            String nodeId = "Core" + i;
            List<String> callees = new ArrayList<>();
            for (int j = 0; j < 100; j++) {
                if (i != j) {
                    callees.add("Core" + j);
                }
            }
            callGraph.put(nodeId, callees);
            
            SymbolInfo symbol = new SymbolInfo();
            symbol.setId(nodeId);
            symbol.setQualifiedName("com.example.core." + nodeId);
            symbol.setName(nodeId);
            symbolTable.put(nodeId, symbol);
        }
        
        // 添加噪音节点 (度数为 1)
        for (int i = 0; i < 500; i++) {
            String noiseId = "Noise" + i;
            callGraph.put(noiseId, List.of("Core0"));
            
            SymbolInfo symbol = new SymbolInfo();
            symbol.setId(noiseId);
            symbol.setQualifiedName("com.example.noise." + noiseId);
            symbol.setName(noiseId);
            symbolTable.put(noiseId, symbol);
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
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("A", List.of("B"));
        callGraph.put("B", List.of("C"));
        callGraph.put("C", List.of("D"));
        callGraph.put("D", Collections.emptyList());
        
        Map<String, SymbolInfo> symbolTable = createSymbolTable("A", "B", "C", "D");
        
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
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("A", List.of("B", "C"));
        callGraph.put("B", List.of("A", "C"));
        callGraph.put("C", List.of("A", "B"));
        
        Map<String, SymbolInfo> symbolTable = createSymbolTable("A", "B", "C");
        
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
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("A", List.of("B"));
        callGraph.put("B", List.of("C"));
        callGraph.put("C", Collections.emptyList());
        
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        
        SymbolInfo symbolA = new SymbolInfo();
        symbolA.setId("A");
        symbolA.setQualifiedName("com.example.service.UserService.methodA");
        symbolA.setName("methodA");
        symbolTable.put("A", symbolA);
        
        SymbolInfo symbolB = new SymbolInfo();
        symbolB.setId("B");
        symbolB.setQualifiedName("com.example.service.UserService.methodB");
        symbolB.setName("methodB");
        symbolTable.put("B", symbolB);
        
        SymbolInfo symbolC = new SymbolInfo();
        symbolC.setId("C");
        symbolC.setQualifiedName("com.example.service.UserService.methodC");
        symbolC.setName("methodC");
        symbolTable.put("C", symbolC);
        
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
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("A", List.of("B"));
        callGraph.put("B", List.of("C"));
        callGraph.put("C", Collections.emptyList());
        
        Map<String, SymbolInfo> symbolTable = createSymbolTable("A", "B", "C");
        
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
        Map<String, List<String>> callGraph = new HashMap<>();
        callGraph.put("A", List.of("B"));
        callGraph.put("B", List.of("C"));
        callGraph.put("C", Collections.emptyList());
        
        Map<String, SymbolInfo> symbolTable = createSymbolTable("A", "B", "C");
        
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
    
    private Map<String, SymbolInfo> createSymbolTable(String... ids) {
        Map<String, SymbolInfo> symbolTable = new HashMap<>();
        for (String id : ids) {
            SymbolInfo symbol = new SymbolInfo();
            symbol.setId(id);
            symbol.setQualifiedName("com.example." + id);
            symbol.setName(id);
            symbolTable.put(id, symbol);
        }
        return symbolTable;
    }
}
