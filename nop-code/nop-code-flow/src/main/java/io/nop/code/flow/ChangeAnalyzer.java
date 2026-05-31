package io.nop.code.flow;

import io.nop.api.core.exceptions.NopException;
import io.nop.code.core.NopCodeCoreErrors;
import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeAnalyzer implements IChangeAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeAnalyzer.class);

    private IFlowDetector flowDetector;

    public void setFlowDetector(IFlowDetector flowDetector) {
        this.flowDetector = flowDetector;
    }

    private static final Pattern DIFF_HEADER_FILE = Pattern.compile("^\\+\\+\\+ b/(.+)$");
    private static final Pattern DIFF_HEADER_OLD_FILE = Pattern.compile("^--- a/(.+)$");
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");
    private static final Pattern RENAME_HEADER = Pattern.compile("^rename from (.+)$");
    private static final Pattern RENAME_TO = Pattern.compile("^rename to (.+)$");
    private static final Pattern GIT_REF_PATTERN = Pattern.compile("^[a-zA-Z0-9._/\\-~]{1,256}$");

    private static final double CAP_FLOW_PARTICIPATION = 0.25;
    private static final double CAP_COMMUNITY_CROSSING = 0.15;
    private static final double DEFAULT_TEST_COVERAGE_GAP = 0.30;
    private static final double MIN_TEST_COVERAGE_GAP = 0.05;
    private static final double WEIGHT_SECURITY_SENSITIVITY = 0.20;
    private static final double CAP_CALLER_COUNT = 0.10;

    private static final Set<String> SECURITY_KEYWORDS = Set.of(
            "auth", "login", "password", "credential", "token", "session",
            "permission", "privilege", "admin", "root", "secret", "key",
            "encrypt", "decrypt", "hash", "salt", "certificate", "ssl",
            "tls", "security", "access", "acl", "role", "verify",
            "authenticate", "authorize", "validate"
    );

    private static final double HIGH_RISK_THRESHOLD = 0.50;
    private static final double MEDIUM_RISK_THRESHOLD = 0.25;

    @Override
    public ChangeAnalysisResult analyzeChanges(String indexId, String baselineCommitish,
                                                String targetCommitish,
                                                SymbolTable symbolTable, CallGraph callGraph) {
        validateGitRef(baselineCommitish);
        validateGitRef(targetCommitish);
        Map<String, List<LineRange>> fileChanges = parseGitDiff(baselineCommitish, targetCommitish, null);

        List<String> changedFiles = new ArrayList<>(fileChanges.keySet());
        List<ChangeAnalysisResult.AffectedSymbol> affectedSymbols = new ArrayList<>();

        List<ExecutionFlow> allFlows = Collections.emptyList();
        if (flowDetector != null) {
            try {
                allFlows = flowDetector.detectFlows(indexId, symbolTable, callGraph);
            } catch (Exception e) {
                LOG.warn("Failed to detect flows for index {}", indexId, e);
            }
        }

        for (Map.Entry<String, List<LineRange>> entry : fileChanges.entrySet()) {
            String filePath = entry.getKey();
            List<LineRange> lineRanges = entry.getValue();
            List<CodeSymbol> symbolsInFile = findSymbolsByFile(symbolTable, filePath);

            for (CodeSymbol symbol : symbolsInFile) {
                if (overlapsAnyRange(symbol, lineRanges)) {
                    List<ExecutionFlow> matchingFlows = findFlowsContainingSymbol(
                            allFlows, symbol, symbolTable);
                    ChangeAnalysisResult.AffectedSymbol affected = buildAffectedSymbol(
                            symbol, symbolTable, callGraph, matchingFlows);
                    affectedSymbols.add(affected);
                }
            }
        }

        affectedSymbols.sort((a, b) -> Double.compare(b.getRiskScore(), a.getRiskScore()));

        ChangeAnalysisResult result = new ChangeAnalysisResult();
        result.setChangedFiles(changedFiles);
        result.setAffectedSymbols(affectedSymbols);
        result.setRiskSummary(computeRiskSummary(affectedSymbols));
        result.setSuggestedActions(buildSuggestedActions(affectedSymbols));
        return result;
    }

    private List<ExecutionFlow> findFlowsContainingSymbol(List<ExecutionFlow> allFlows,
                                                           CodeSymbol symbol,
                                                           SymbolTable symbolTable) {
        List<ExecutionFlow> matching = new ArrayList<>();
        String symbolId = symbol.getId();
        for (ExecutionFlow flow : allFlows) {
            if (flow.getPathNodeIds() != null && flow.getPathNodeIds().contains(symbolId)) {
                matching.add(flow);
            }
            if (flow.getEntryPointSymbolId() != null && flow.getEntryPointSymbolId().equals(symbolId)) {
                if (matching.stream().noneMatch(f -> f.getId().equals(flow.getId()))) {
                    matching.add(flow);
                }
            }
        }
        return matching;
    }

    protected Map<String, List<LineRange>> parseGitDiff(String baseline, String target, String workingDirectory) {
        Map<String, List<LineRange>> result = new LinkedHashMap<>();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "diff", baseline + ".." + target, "--unified=0");
            if (workingDirectory != null) {
                pb.directory(new java.io.File(workingDirectory));
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String currentFile = null;
                    String oldFilePath = null;
                    boolean skipBinary = false;
                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("Binary files")) {
                            skipBinary = true;
                            continue;
                        }

                        Matcher fileMatcher = DIFF_HEADER_FILE.matcher(line);
                        if (fileMatcher.matches()) {
                            currentFile = fileMatcher.group(1);
                            skipBinary = false;
                            continue;
                        }

                        Matcher oldFileMatcher = DIFF_HEADER_OLD_FILE.matcher(line);
                        if (oldFileMatcher.matches()) {
                            oldFilePath = oldFileMatcher.group(1);
                            continue;
                        }

                        Matcher renameFromMatcher = RENAME_HEADER.matcher(line);
                        if (renameFromMatcher.matches()) {
                            oldFilePath = renameFromMatcher.group(1);
                            continue;
                        }

                        Matcher renameToMatcher = RENAME_TO.matcher(line);
                        if (renameToMatcher.matches()) {
                            String newName = renameToMatcher.group(1);
                            if (oldFilePath != null && result.containsKey(oldFilePath)) {
                                List<LineRange> ranges = result.remove(oldFilePath);
                                result.put(newName, ranges);
                            }
                            currentFile = newName;
                            continue;
                        }

                        if (skipBinary || currentFile == null) {
                            continue;
                        }

                        Matcher hunkMatcher = HUNK_HEADER.matcher(line);
                        if (hunkMatcher.matches()) {
                            int startLine = Integer.parseInt(hunkMatcher.group(3));
                            String countStr = hunkMatcher.group(4);
                            int count = (countStr != null && !countStr.isEmpty()) ? Integer.parseInt(countStr) : 1;

                            result.computeIfAbsent(currentFile, k -> new ArrayList<>())
                                    .add(new LineRange(startLine, startLine + count - 1));
                        }
                    }
                }

                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    LOG.warn("Git diff process timed out after 30 seconds for {}..{}", baseline, target);
                }
            } finally {
                process.destroyForcibly();
            }
        } catch (IOException e) {
            LOG.warn("Failed to parse git diff output", e);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while parsing git diff output", e);
            return result;
        }

        return result;
    }

    private List<CodeSymbol> findSymbolsByFile(SymbolTable symbolTable, String filePath) {
        List<CodeSymbol> result = new ArrayList<>();
        for (CodeSymbol symbol : symbolTable.getAll()) {
            String qn = symbol.getQualifiedName();
            String symFilePath = extractFilePathFromSymbol(symbol);
            if (symFilePath != null && filePathMatches(symFilePath, filePath)) {
                result.add(symbol);
            } else if (qn != null && pathMatchesQualifiedName(filePath, qn)) {
                result.add(symbol);
            }
        }
        return result;
    }

    private static boolean filePathMatches(String symbolFilePath, String changedFilePath) {
        String normalized1 = symbolFilePath.replace('\\', '/');
        String normalized2 = changedFilePath.replace('\\', '/');
        return normalized1.equals(normalized2) || normalized1.endsWith("/" + normalized2) || normalized2.endsWith("/" + normalized1);
    }

    private static String extractFilePathFromSymbol(CodeSymbol symbol) {
        String extData = symbol.getExtData();
        if (extData != null && extData.contains("filePath")) {
            try {
                Object parsed = io.nop.core.lang.json.JsonTool.parseNonStrict(extData);
                if (parsed instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) parsed;
                    Object filePath = map.get("filePath");
                    if (filePath != null) {
                        return filePath.toString();
                    }
                }
            } catch (Exception e) {
                // fall through
            }
        }
        return null;
    }

    private boolean pathMatchesQualifiedName(String filePath, String qualifiedName) {
        String normalized = filePath.replace('\\', '/');
        int parenIndex = qualifiedName.indexOf('(');
        String qn = parenIndex > 0 ? qualifiedName.substring(0, parenIndex) : qualifiedName;

        String dotted = qn.replace('.', '/');

        if (pathSegmentMatch(normalized, dotted)) {
            return true;
        }

        int slashCount = 0;
        for (int i = 0; i < dotted.length(); i++) {
            if (dotted.charAt(i) == '/') slashCount++;
        }

        if (slashCount >= 3) {
            int lastSlash = dotted.lastIndexOf('/');
            String parentPath = dotted.substring(0, lastSlash);
            if (pathSegmentMatch(normalized, parentPath)) {
                return true;
            }
        }

        return false;
    }

    private boolean pathSegmentMatch(String normalized, String pathSegment) {
        int idx = normalized.indexOf(pathSegment);
        while (idx >= 0) {
            int endIdx = idx + pathSegment.length();
            if (endIdx == normalized.length()) {
                return true;
            }
            char next = normalized.charAt(endIdx);
            if (next == '.' || next == '/') {
                return true;
            }
            idx = normalized.indexOf(pathSegment, endIdx);
        }
        return false;
    }

    private boolean overlapsAnyRange(CodeSymbol symbol, List<LineRange> ranges) {
        int symStart = symbol.getLine();
        int symEnd = symbol.getEndLine() > 0 ? symbol.getEndLine() : symStart;
        for (LineRange range : ranges) {
            if (symStart <= range.end && symEnd >= range.start) {
                return true;
            }
        }
        return false;
    }

    private ChangeAnalysisResult.AffectedSymbol buildAffectedSymbol(CodeSymbol symbol,
                                                                     SymbolTable symbolTable,
                                                                     CallGraph callGraph,
                                                                     List<ExecutionFlow> affectedFlows) {
        ChangeAnalysisResult.RiskBreakdown breakdown = new ChangeAnalysisResult.RiskBreakdown();

        double flowParticipation = computeFlowParticipation(symbol, callGraph);
        double communityCrossing = computeCommunityCrossing(symbol, callGraph, symbolTable);
        double testCoverageGap = computeTestCoverageGap(symbol);
        double securitySensitivity = computeSecuritySensitivity(symbol);
        double callerCountScore = computeCallerCountScore(symbol, callGraph);

        breakdown.setFlowParticipation(flowParticipation);
        breakdown.setCommunityCrossing(communityCrossing);
        breakdown.setTestCoverageGap(testCoverageGap);
        breakdown.setSecuritySensitivity(securitySensitivity);
        breakdown.setCallerCount(callerCountScore);

        double totalRisk = flowParticipation + communityCrossing + testCoverageGap
                + securitySensitivity + callerCountScore;

        ChangeAnalysisResult.AffectedSymbol affected = new ChangeAnalysisResult.AffectedSymbol();
        affected.setSymbolId(symbol.getId());
        affected.setQualifiedName(symbol.getQualifiedName());
        affected.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
        affected.setRiskScore(totalRisk);
        affected.setRiskBreakdown(breakdown);
        affected.setAffectedFlows(affectedFlows != null ? affectedFlows : Collections.emptyList());
        return affected;
    }

    private double computeFlowParticipation(CodeSymbol symbol, CallGraph callGraph) {
        if (symbol.getId() == null) return 0.0;
        List<String> callees = callGraph.getCallees(symbol.getId());
        List<String> callers = callGraph.getCallers(symbol.getId());
        double score = (!callees.isEmpty() || !callers.isEmpty()) ? 0.15 : 0.0;
        return Math.min(score, CAP_FLOW_PARTICIPATION);
    }

    private double computeCommunityCrossing(CodeSymbol symbol, CallGraph callGraph,
                                            SymbolTable symbolTable) {
        if (symbol.getId() == null) return 0.0;
        List<String> callers = callGraph.getCallers(symbol.getId());
        if (callers.size() <= 1) return 0.0;

        Set<String> packages = new HashSet<>();
        for (String callerId : callers) {
            CodeSymbol caller = symbolTable.getById(callerId);
            if (caller != null && caller.getQualifiedName() != null) {
                int lastDot = caller.getQualifiedName().lastIndexOf('.');
                if (lastDot > 0) {
                    packages.add(caller.getQualifiedName().substring(0, lastDot));
                }
            }
        }

        return packages.size() > 1 ? CAP_COMMUNITY_CROSSING : 0.0;
    }

    private double computeTestCoverageGap(CodeSymbol symbol) {
        CodeSymbolKind kind = symbol.getKind();
        if (kind == CodeSymbolKind.CONSTRUCTOR) {
            return MIN_TEST_COVERAGE_GAP;
        }
        String qn = symbol.getQualifiedName();
        if (qn == null) return DEFAULT_TEST_COVERAGE_GAP;
        String lower = qn.toLowerCase();
        if (lower.contains("test") || lower.contains("spec")) {
            return MIN_TEST_COVERAGE_GAP;
        }
        return DEFAULT_TEST_COVERAGE_GAP;
    }

    private double computeSecuritySensitivity(CodeSymbol symbol) {
        String name = symbol.getName();
        String qn = symbol.getQualifiedName();
        String target = (qn != null ? qn : (name != null ? name : "")).toLowerCase();
        for (String keyword : SECURITY_KEYWORDS) {
            if (target.contains(keyword)) {
                return WEIGHT_SECURITY_SENSITIVITY;
            }
        }
        return 0.0;
    }

    private double computeCallerCountScore(CodeSymbol symbol, CallGraph callGraph) {
        if (symbol.getId() == null) return 0.0;
        int count = callGraph.getCallers(symbol.getId()).size();
        return Math.min(count / 20.0, CAP_CALLER_COUNT);
    }

    private ChangeAnalysisResult.RiskSummary computeRiskSummary(
            List<ChangeAnalysisResult.AffectedSymbol> symbols) {
        ChangeAnalysisResult.RiskSummary summary = new ChangeAnalysisResult.RiskSummary();
        int high = 0, medium = 0, low = 0;
        for (ChangeAnalysisResult.AffectedSymbol s : symbols) {
            double score = s.getRiskScore();
            if (score >= HIGH_RISK_THRESHOLD) {
                high++;
            } else if (score >= MEDIUM_RISK_THRESHOLD) {
                medium++;
            } else {
                low++;
            }
        }
        summary.setHigh(high);
        summary.setMedium(medium);
        summary.setLow(low);
        return summary;
    }

    private List<String> buildSuggestedActions(List<ChangeAnalysisResult.AffectedSymbol> symbols) {
        List<String> actions = new ArrayList<>();
        boolean hasHighRisk = false;
        boolean hasSecurityRisk = false;
        boolean hasTestGap = false;

        for (ChangeAnalysisResult.AffectedSymbol s : symbols) {
            if (s.getRiskScore() >= HIGH_RISK_THRESHOLD) hasHighRisk = true;
            if (s.getRiskBreakdown() != null) {
                if (s.getRiskBreakdown().getSecuritySensitivity() > 0) hasSecurityRisk = true;
                if (s.getRiskBreakdown().getTestCoverageGap() >= DEFAULT_TEST_COVERAGE_GAP) hasTestGap = true;
            }
        }

        if (hasHighRisk) {
            actions.add("Review high-risk changes with extra scrutiny before merging");
        }
        if (hasSecurityRisk) {
            actions.add("Security review required for changes touching security-sensitive symbols");
        }
        if (hasTestGap) {
            actions.add("Add tests for changed symbols with insufficient coverage");
        }
        if (!symbols.isEmpty()) {
            actions.add("Run full integration test suite to validate impact");
        }

        return actions;
    }

    private void validateGitRef(String ref) {
        if (ref == null || !GIT_REF_PATTERN.matcher(ref).matches()) {
            throw new NopException(NopCodeCoreErrors.ERR_CODE_INVALID_GIT_REF)
                    .param(NopCodeCoreErrors.ARG_GIT_REF, ref);
        }
    }

    protected static class LineRange {
        final int start;
        final int end;

        LineRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }
}
