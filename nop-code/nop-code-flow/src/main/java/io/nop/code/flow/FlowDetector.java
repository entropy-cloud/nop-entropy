package io.nop.code.flow;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.util.ExtDataHelper;
import io.nop.code.graph.entrypoint.EntryPointScorer;
public class FlowDetector implements IFlowDetector {

    private static final Logger LOG = LoggerFactory.getLogger(FlowDetector.class);

    private static final int DEFAULT_MAX_DEPTH = 15;
    private static final int MAX_CACHE_ENTRIES = 20;

    private static final double WEIGHT_FILE_SPREAD = 0.30;
    private static final double WEIGHT_EXTERNAL = 0.20;
    private static final double WEIGHT_SECURITY = 0.25;
    private static final double WEIGHT_TEST_GAP = 0.15;
    private static final double WEIGHT_DEPTH = 0.10;

    private static final Set<String> EXTERNAL_PREFIXES = Set.of(
            "java.", "javax.", "jakarta.",
            "org.springframework.", "org.apache.",
            "org.hibernate.", "com.google.",
            "com.fasterxml.", "org.slf4j.",
            "org.jetbrains.", "kotlin.",
            "scala.", "groovy.",
            "reactor.", "io.netty."
    );

    private static final Pattern SECURITY_PATTERN = Pattern.compile(
            "(?i)(auth|login|encrypt|password|passwd|token|validate|sanitize|escape|credential|secret|cipher|decrypt|hash|salt|permission|privilege|acl|rbac|oauth|jwt|session|csrf|xss)"
    );

    private static final Pattern ENTRY_POINT_NAME_PATTERN = Pattern.compile(
            "^(main|handle.*|process.*|onEvent.*|run|execute|doHandle|doProcess|handleRequest|processMessage)$"
    );

    private static final Set<String> SPRING_ENTRY_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping",
            "org.springframework.scheduling.annotation.Scheduled",
            "org.springframework.jms.annotation.JmsListener",
            "org.springframework.kafka.annotation.KafkaListener",
            "org.springframework.amqp.rabbit.annotation.RabbitListener",
            "org.springframework.messaging.handler.annotation.MessageMapping",
            "org.springframework.context.event.EventListener",
            "org.springframework.boot.context.event.ApplicationReadyEvent"
    );

    private final List<IEntryPointPatternProvider> patternProviders;

    private final Map<String, List<ExecutionFlow>> flowCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> symbolFilePathCache = new ConcurrentHashMap<>();

    private int maxDepth = DEFAULT_MAX_DEPTH;

    public FlowDetector() {
        this.patternProviders = List.of(new DefaultSpringEntryPointPatternProvider());
    }

    public FlowDetector(List<IEntryPointPatternProvider> patternProviders) {
        this.patternProviders = patternProviders != null
                ? patternProviders.stream()
                .sorted(Comparator.comparingInt(IEntryPointPatternProvider::priority).reversed())
                .collect(Collectors.toList())
                : List.of(new DefaultSpringEntryPointPatternProvider());
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public List<ExecutionFlow> detectFlows(String indexId, SymbolTable symbolTable, CallGraph callGraph) {
        Map<String, String> filePathMap = new HashMap<>();
        for (CodeSymbol symbol : symbolTable.getAll()) {
            String filePath = extractFilePathFromSymbol(symbol);
            if (filePath != null && symbol.getId() != null) {
                filePathMap.put(symbol.getId(), filePath);
            }
        }
        symbolFilePathCache.put(indexId, filePathMap);

        List<EntryPointScorer.EntryPointScore> scores =
                new EntryPointScorer().scoreEntryPoints(callGraph, symbolTable);

        List<EntryPointScorer.EntryPointScore> entryPoints = EntryPointScorer.getEntryPoints(scores);

        Set<String> annotationEntryIds = findAnnotationEntryPoints(symbolTable);

        Set<String> allEntryIds = new LinkedHashSet<>();
        for (EntryPointScorer.EntryPointScore ep : entryPoints) {
            allEntryIds.add(ep.getSymbolId());
        }
        allEntryIds.addAll(annotationEntryIds);

        for (CodeSymbol symbol : symbolTable.getAll()) {
            if (isEntryPointByName(symbol)) {
                allEntryIds.add(symbol.getId());
            }
        }

        List<ExecutionFlow> flows = new ArrayList<>();
        for (String entryId : allEntryIds) {
            CodeSymbol entrySymbol = symbolTable.getById(entryId);
            if (entrySymbol == null) {
                continue;
            }

            TraversalResult result = traceForward(entryId, callGraph, symbolTable);
            if (result.pathNodeIds.isEmpty()) {
                continue;
            }

            ExecutionFlow flow = buildFlow(indexId, entrySymbol, result, symbolTable);
            flows.add(flow);
        }

        flowCache.put(indexId, flows);
        evictOverflow(flowCache);
        evictOverflow(symbolFilePathCache);
        LOG.info("Detected {} execution flows for index {}", flows.size(), indexId);
        return flows;
    }

    @Override
    public ExecutionFlow getFlow(String indexId, String flowId) {
        List<ExecutionFlow> flows = flowCache.get(indexId);
        if (flows == null) {
            return null;
        }
        return flows.stream()
                .filter(f -> f.getId().equals(flowId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<ExecutionFlow> listFlows(String indexId) {
        return flowCache.getOrDefault(indexId, Collections.emptyList());
    }

    @Override
    public List<ExecutionFlow> getAffectedFlows(String indexId, List<String> changedFilePaths) {
        List<ExecutionFlow> allFlows = flowCache.getOrDefault(indexId, Collections.emptyList());
        if (allFlows.isEmpty() || changedFilePaths == null || changedFilePaths.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> changedSet = new HashSet<>(changedFilePaths);

        List<ExecutionFlow> affected = new ArrayList<>();
        for (ExecutionFlow flow : allFlows) {
            if (isFlowAffected(flow, changedSet)) {
                affected.add(flow);
            }
        }
        return affected;
    }

    private boolean isFlowAffected(ExecutionFlow flow, Set<String> changedFilePaths) {
        String indexId = flow.getIndexId();
        Map<String, String> filePathMap = symbolFilePathCache.get(indexId);

        String entryQn = flow.getEntryPointQualifiedName();
        if (entryQn != null) {
            String entryFile = qualifiedNameToFilePath(entryQn);
            if (entryFile != null && changedFilePaths.contains(entryFile)) {
                return true;
            }
        }
        if (flow.getPathNodeIds() != null) {
            for (String nodeId : flow.getPathNodeIds()) {
                if (filePathMap != null) {
                    String file = filePathMap.get(nodeId);
                    if (file != null && changedFilePaths.contains(file)) {
                        return true;
                    }
                } else {
                    String file = symbolIdToFilePath(nodeId);
                    if (file != null && changedFilePaths.contains(file)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final List<String> SOURCE_EXTENSIONS = List.of(".java", ".py", ".ts", ".tsx");

    private String qualifiedNameToFilePath(String qualifiedName) {
        if (qualifiedName == null) return null;
        int parenIndex = qualifiedName.indexOf('(');
        if (parenIndex > 0) {
            qualifiedName = qualifiedName.substring(0, parenIndex);
        }
        int lastDot = qualifiedName.lastIndexOf('.');
        if (lastDot > 0) {
            String classPath = qualifiedName.substring(0, lastDot).replace('.', '/');
            return classPath + ".java";
        }
        return null;
    }

    private String symbolIdToFilePath(String symbolId) {
        if (symbolId == null) return null;
        int parenIndex = symbolId.indexOf('(');
        String base = parenIndex > 0 ? symbolId.substring(0, parenIndex) : symbolId;
        if (base.contains(".")) {
            int lastDot = base.lastIndexOf('.');
            String path = base.substring(0, lastDot).replace('.', '/');
            return path + ".java";
        }
        return null;
    }

    private TraversalResult traceForward(String entryId, CallGraph callGraph, SymbolTable symbolTable) {
        TraversalResult result = new TraversalResult();
        Set<String> visited = new HashSet<>();

        Queue<TraversalNode> queue = new LinkedList<>();
        queue.add(new TraversalNode(entryId, 0));

        while (!queue.isEmpty()) {
            TraversalNode current = queue.poll();

            if (visited.contains(current.symbolId)) {
                continue;
            }

            CodeSymbol symbol = symbolTable.getById(current.symbolId);
            if (symbol != null && isExternalPackage(symbol.getQualifiedName())) {
                continue;
            }

            visited.add(current.symbolId);
            result.pathNodeIds.add(current.symbolId);
            result.maxDepthReached = Math.max(result.maxDepthReached, current.depth);

            if (current.depth >= maxDepth) {
                continue;
            }

            List<String> callees = callGraph.getCallees(current.symbolId);
            for (String calleeId : callees) {
                if (!visited.contains(calleeId)) {
                    queue.add(new TraversalNode(calleeId, current.depth + 1));
                }
            }
        }

        result.totalCalls = 0;
        for (String nodeId : result.pathNodeIds) {
            result.totalCalls += callGraph.getCallees(nodeId).size();
        }

        return result;
    }

    private ExecutionFlow buildFlow(String indexId, CodeSymbol entrySymbol,
                                    TraversalResult result, SymbolTable symbolTable) {
        ExecutionFlow flow = new ExecutionFlow();
        flow.setId(generateFlowId(entrySymbol.getId()));
        flow.setName(extractFlowName(entrySymbol));
        flow.setIndexId(indexId);
        flow.setEntryPointSymbolId(entrySymbol.getId());
        flow.setEntryPointQualifiedName(entrySymbol.getQualifiedName());
        flow.setDepth(result.maxDepthReached);
        flow.setPathNodeIds(result.pathNodeIds);

        Set<String> uniqueFiles = new HashSet<>();
        for (String nodeId : result.pathNodeIds) {
            CodeSymbol symbol = symbolTable.getById(nodeId);
            if (symbol != null) {
                String fileKey = extractFileKey(symbol);
                uniqueFiles.add(fileKey);
            }
        }

        ExecutionFlow.FlowStats stats = new ExecutionFlow.FlowStats();
        stats.setFileCount(uniqueFiles.size());
        stats.setSymbolCount(result.pathNodeIds.size());
        stats.setMaxDepth(result.maxDepthReached);
        flow.setStats(stats);

        double criticality = computeCriticality(result, symbolTable, uniqueFiles.size());
        flow.setCriticality(criticality);

        return flow;
    }

    private double computeCriticality(TraversalResult result, SymbolTable symbolTable, int fileCount) {
        int symbolCount = result.pathNodeIds.size();
        if (symbolCount == 0) {
            return 0.0;
        }

        double fileSpread = Math.min((fileCount - 1) / 4.0, 1.0);
        if (fileCount <= 1) {
            fileSpread = 0.0;
        }

        int externalCalls = 0;
        int securitySymbols = 0;
        for (String nodeId : result.pathNodeIds) {
            CodeSymbol symbol = symbolTable.getById(nodeId);
            if (symbol == null) {
                continue;
            }

            if (isExternalCall(symbol.getQualifiedName())) {
                externalCalls++;
            }

            if (isSecuritySensitive(symbol)) {
                securitySymbols++;
            }
        }

        double externalScore = result.totalCalls > 0
                ? (double) externalCalls / result.totalCalls
                : 0.0;

        double securityScore = (double) securitySymbols / symbolCount;

        double testGap = 1.0;

        double depthScore = (double) result.maxDepthReached / maxDepth;

        return fileSpread * WEIGHT_FILE_SPREAD
                + externalScore * WEIGHT_EXTERNAL
                + securityScore * WEIGHT_SECURITY
                + testGap * WEIGHT_TEST_GAP
                + depthScore * WEIGHT_DEPTH;
    }

    private Set<String> findAnnotationEntryPoints(SymbolTable symbolTable) {
        Set<String> entryIds = new HashSet<>();
        for (CodeSymbol symbol : symbolTable.getAll()) {
            if (symbol.getKind() == null) {
                continue;
            }
            for (IEntryPointPatternProvider provider : patternProviders) {
                if (provider.isEntryPoint(symbol)) {
                    entryIds.add(symbol.getId());
                    break;
                }
            }
        }
        return entryIds;
    }

    private boolean isEntryPointByName(CodeSymbol symbol) {
        if (symbol.getName() == null) {
            return false;
        }
        return ENTRY_POINT_NAME_PATTERN.matcher(symbol.getName()).matches();
    }

    private boolean isExternalPackage(String qualifiedName) {
        if (qualifiedName == null) {
            return false;
        }
        for (String prefix : EXTERNAL_PREFIXES) {
            if (qualifiedName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExternalCall(String qualifiedName) {
        return isExternalPackage(qualifiedName);
    }

    private boolean isSecuritySensitive(CodeSymbol symbol) {
        String qn = symbol.getQualifiedName();
        String name = symbol.getName();
        if (qn != null && SECURITY_PATTERN.matcher(qn).find()) {
            return true;
        }
        return name != null && SECURITY_PATTERN.matcher(name).find();
    }

    private String extractFileKey(CodeSymbol symbol) {
        String qn = symbol.getQualifiedName();
        if (qn == null) {
            return symbol.getId();
        }
        int parenIndex = qn.indexOf('(');
        if (parenIndex > 0) {
            qn = qn.substring(0, parenIndex);
        }
        int lastDot = qn.lastIndexOf('.');
        if (lastDot > 0) {
            return qn.substring(0, lastDot);
        }
        return qn;
    }

    private String extractFlowName(CodeSymbol entrySymbol) {
        String qn = entrySymbol.getQualifiedName();
        if (qn == null) {
            return entrySymbol.getName();
        }
        return qn;
    }

    private String generateFlowId(String entrySymbolId) {
        return "flow-" + entrySymbolId;
    }

    private static class TraversalResult {
        List<String> pathNodeIds = new ArrayList<>();
        int maxDepthReached = 0;
        int totalCalls = 0;
    }

    private static class TraversalNode {
        final String symbolId;
        final int depth;

        TraversalNode(String symbolId, int depth) {
            this.symbolId = symbolId;
            this.depth = depth;
        }
    }

    private static class DefaultSpringEntryPointPatternProvider implements IEntryPointPatternProvider {

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public boolean isEntryPoint(CodeSymbol symbol) {
            // Annotation data is not available in the in-memory CodeSymbol model.
            // Instead, match Spring entry point patterns by checking the symbol's
            // qualified name against common Spring endpoint naming conventions.
            String qn = symbol.getQualifiedName();
            if (qn == null) {
                return false;
            }

            // Check if the symbol's class name suggests a Spring component
            String className = extractClassName(qn);
            if (className != null) {
                if (className.endsWith("Controller") || className.endsWith("RestController")
                        || className.endsWith("Endpoint") || className.endsWith("Listener")
                        || className.endsWith("Handler") || className.endsWith("Scheduler")
                        || className.endsWith("Consumer") || className.endsWith("Subscriber")) {
                    // Methods in these classes are likely entry points
                    CodeSymbolKind kind = symbol.getKind();
                    if (kind == CodeSymbolKind.METHOD || kind == CodeSymbolKind.CONSTRUCTOR) {
                        return true;
                    }
                }
            }

            // Check extData for annotation short names
            String extData = symbol.getExtData();
            if (extData != null) {
                for (String annotation : SPRING_ENTRY_ANNOTATIONS) {
                    String shortName = annotation.substring(annotation.lastIndexOf('.') + 1);
                    if (extData.contains(shortName)) {
                        return true;
                    }
                }
            }

            return false;
        }

        private static String extractClassName(String qualifiedName) {
            // e.g., "com.example.controller.UserController.getMethod" -> "UserController"
            int parenIdx = qualifiedName.indexOf('(');
            String withoutParams = parenIdx > 0 ? qualifiedName.substring(0, parenIdx) : qualifiedName;
            int lastDot = withoutParams.lastIndexOf('.');
            if (lastDot < 0) return null;
            String beforeMethod = withoutParams.substring(0, lastDot);
            int prevDot = beforeMethod.lastIndexOf('.');
            return prevDot >= 0 ? beforeMethod.substring(prevDot + 1) : beforeMethod;
        }

        @Override
        public List<String> getAnnotationPatterns() {
            return List.copyOf(SPRING_ENTRY_ANNOTATIONS);
        }

        @Override
        public List<String> getNamePatterns() {
            return List.of("main", "handle*", "process*", "onEvent*");
        }
    }

    private static String extractFilePathFromSymbol(CodeSymbol symbol) {
        return ExtDataHelper.extractFilePath(symbol.getExtData());
    }

    public void invalidateCache(String indexId) {
        flowCache.remove(indexId);
        symbolFilePathCache.remove(indexId);
    }

    private void evictOverflow(Map<String, ?> cache) {
        while (cache.size() > MAX_CACHE_ENTRIES) {
            String key = cache.keySet().stream().findFirst().orElse(null);
            if (key == null) break;
            cache.remove(key);
        }
    }
}
