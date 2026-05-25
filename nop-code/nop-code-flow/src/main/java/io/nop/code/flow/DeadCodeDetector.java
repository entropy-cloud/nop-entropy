package io.nop.code.flow;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeAccessModifier;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DeadCodeDetector implements IDeadCodeDetector {

    private static final Set<String> DEFAULT_FRAMEWORK_ANNOTATIONS = Set.of(
            "RequestMapping",
            "GetMapping",
            "PostMapping",
            "PutMapping",
            "DeleteMapping",
            "PatchMapping",
            "Scheduled",
            "KafkaListener",
            "RabbitListener",
            "MessageMapping",
            "ManagedOperation",
            "ManagedAttribute",
            "Observes",
            "Subscribe",
            "EventListener",
            "BizModel",
            "BizQuery",
            "BizMutation",
            "BizLoader",
            "BizAction",
            "EventHandler"
    );

    private static final Set<String> DEFAULT_DUNDER_METHODS = Set.of(
            "__init__",
            "__str__",
            "__repr__",
            "__len__",
            "__getitem__",
            "__setitem__",
            "__delitem__",
            "__iter__",
            "__next__",
            "__call__",
            "__eq__",
            "__hash__",
            "__bool__",
            "__enter__",
            "__exit__",
            "__getattr__",
            "__setattr__",
            "__delattr__",
            "__contains__",
            "__add__",
            "__sub__",
            "__mul__",
            "__truediv__",
            "__floordiv__",
            "__mod__",
            "__pow__",
            "__lt__",
            "__le__",
            "__gt__",
            "__ge__",
            "__ne__",
            "__new__",
            "__del__",
            "__slots__",
            "__class__"
    );

    private static final Set<String> DEFAULT_PYTHON_DECORATORS = Set.of(
            "property",
            "abstractmethod",
            "dataclass",
            "staticmethod",
            "classmethod"
    );

    private static final Set<String> DEFAULT_ORM_BASE_CLASSES = Set.of(
            "Entity",
            "BaseEntity",
            "MappedSuperclass"
    );

    private static final Set<String> DEFAULT_CONVENTION_ENTRY_NAMES = Set.of(
            "main"
    );

    private final Set<String> frameworkAnnotations;
    private final Set<String> dunderMethods;
    private final Set<String> pythonDecorators;
    private final Set<String> ormBaseClassPatterns;
    private final Set<String> conventionEntryNames;
    private final List<Pattern> additionalExcludePatterns;
    private final List<Pattern> testPathPatterns;

    public DeadCodeDetector() {
        this(Collections.emptyList());
    }

    public DeadCodeDetector(List<String> excludePatterns) {
        this.frameworkAnnotations = new HashSet<>(DEFAULT_FRAMEWORK_ANNOTATIONS);
        this.dunderMethods = new HashSet<>(DEFAULT_DUNDER_METHODS);
        this.pythonDecorators = new HashSet<>(DEFAULT_PYTHON_DECORATORS);
        this.ormBaseClassPatterns = new HashSet<>(DEFAULT_ORM_BASE_CLASSES);
        this.conventionEntryNames = new HashSet<>(DEFAULT_CONVENTION_ENTRY_NAMES);
        this.additionalExcludePatterns = excludePatterns.stream()
                .map(Pattern::compile)
                .collect(Collectors.toList());
        this.testPathPatterns = List.of(
                Pattern.compile("/test/"),
                Pattern.compile("/tests/"),
                Pattern.compile("/__tests__/"),
                Pattern.compile("Test\\.java$"),
                Pattern.compile("Test\\.kt$"),
                Pattern.compile("_test\\.py$"),
                Pattern.compile("test_.*\\.py$"),
                Pattern.compile("\\.spec\\.ts$"),
                Pattern.compile("\\.test\\.ts$")
        );
    }

    @Override
    public DeadCodeReport detectDeadCode(String indexId, SymbolTable symbolTable, CallGraph callGraph) {
        List<DeadCodeReport.DeadCodeEntry> deadSymbols = new ArrayList<>();
        List<DeadCodeReport.DeadCodeEntry> suspiciousSymbols = new ArrayList<>();

        for (CodeSymbol symbol : symbolTable.getAll()) {
            if (isExcluded(symbol)) {
                continue;
            }

            String symbolId = symbol.getId();
            List<String> callers = callGraph.getCallers(symbolId);
            boolean hasCallers = !callers.isEmpty();

            if (hasCallers) {
                continue;
            }

            DeadCodeReport.DeadCodeEntry entry = buildEntry(symbol);
            double confidence = computeConfidence(symbol);

            entry.setConfidence(confidence);

            if (confidence >= 0.9) {
                entry.setReason("No callers found and no dynamic invocation indicators");
                deadSymbols.add(entry);
            } else if (confidence >= 0.5) {
                entry.setReason("No callers found but may be dynamically invoked");
                suspiciousSymbols.add(entry);
            }
        }

        DeadCodeReport report = new DeadCodeReport();
        report.setDeadSymbols(deadSymbols);
        report.setSuspiciousSymbols(suspiciousSymbols);

        DeadCodeReport.DeadCodeStats stats = new DeadCodeReport.DeadCodeStats();
        stats.setTotal(symbolTable.size());
        stats.setDead(deadSymbols.size());
        stats.setSuspicious(suspiciousSymbols.size());
        report.setStats(stats);

        return report;
    }

    private boolean isExcluded(CodeSymbol symbol) {
        if (symbol.getKind() == CodeSymbolKind.CONSTRUCTOR) {
            return true;
        }

        if (symbol.getKind() == CodeSymbolKind.PARAMETER
                || symbol.getKind() == CodeSymbolKind.LOCAL_VARIABLE
                || symbol.getKind() == CodeSymbolKind.TYPE_PARAMETER
                || symbol.getKind() == CodeSymbolKind.IMPORT
                || symbol.getKind() == CodeSymbolKind.NAMESPACE) {
            return true;
        }

        if (symbol.isAbstractFlag()) {
            return true;
        }

        String name = symbol.getName();
        if (name != null && conventionEntryNames.contains(name)) {
            return true;
        }

        if (isFrameworkEntryPoint(symbol)) {
            return true;
        }

        if (isTestSymbol(symbol)) {
            return true;
        }

        if (isOrmEntitySubclass(symbol)) {
            return true;
        }

        if (isDunderMethod(symbol)) {
            return true;
        }

        if (isDecoratedMethod(symbol)) {
            return true;
        }

        if (matchesAdditionalPatterns(symbol)) {
            return true;
        }

        return false;
    }

    private boolean isFrameworkEntryPoint(CodeSymbol symbol) {
        String signature = symbol.getSignature();
        if (signature == null) {
            return false;
        }
        for (String annotation : frameworkAnnotations) {
            if (signature.contains("@" + annotation)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTestSymbol(CodeSymbol symbol) {
        String filePath = resolveFilePath(symbol);
        if (filePath != null) {
            for (Pattern pattern : testPathPatterns) {
                if (pattern.matcher(filePath).find()) {
                    return true;
                }
            }
        }

        String qName = symbol.getQualifiedName();
        if (qName != null) {
            int dotIdx = qName.lastIndexOf('.');
            if (dotIdx > 0) {
                String className = qName.substring(0, dotIdx);
                int lastDot = className.lastIndexOf('.');
                String simpleClassName = lastDot >= 0 ? className.substring(lastDot + 1) : className;
                if (simpleClassName.endsWith("Test") || simpleClassName.endsWith("Tests")) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isOrmEntitySubclass(CodeSymbol symbol) {
        String superClassName = symbol.getSuperClassName();
        if (superClassName == null) {
            return false;
        }
        for (String pattern : ormBaseClassPatterns) {
            if (superClassName.contains(pattern)) {
                return true;
            }
        }

        String signature = symbol.getSignature();
        if (signature != null && signature.contains("@Entity")) {
            return true;
        }

        return false;
    }

    private boolean isDunderMethod(CodeSymbol symbol) {
        String name = symbol.getName();
        return name != null && dunderMethods.contains(name);
    }

    private boolean isDecoratedMethod(CodeSymbol symbol) {
        String signature = symbol.getSignature();
        if (signature == null) {
            return false;
        }
        for (String decorator : pythonDecorators) {
            if (signature.contains("@" + decorator)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAdditionalPatterns(CodeSymbol symbol) {
        String qName = symbol.getQualifiedName();
        if (qName == null) {
            return false;
        }
        for (Pattern pattern : additionalExcludePatterns) {
            if (pattern.matcher(qName).matches()) {
                return true;
            }
        }
        return false;
    }

    private double computeConfidence(CodeSymbol symbol) {
        if (isPotentiallyDynamic(symbol)) {
            return 0.6;
        }

        if (symbol.getKind() == CodeSymbolKind.FIELD && !isPrivate(symbol)) {
            return 0.7;
        }

        if (symbol.getKind() == CodeSymbolKind.METHOD && !isPrivate(symbol) && !symbol.isStaticFlag()) {
            return 0.75;
        }

        return 0.95;
    }

    private boolean isPrivate(CodeSymbol symbol) {
        return symbol.getAccessModifier() == CodeAccessModifier.PRIVATE;
    }

    private boolean isPotentiallyDynamic(CodeSymbol symbol) {
        String signature = symbol.getSignature();
        if (signature != null) {
            if (signature.contains("Bean")
                    || signature.contains("Component")
                    || signature.contains("Service")
                    || signature.contains("Repository")
                    || signature.contains("Controller")
                    || signature.contains("Inject")
                    || signature.contains("Autowired")
                    || signature.contains("Resource")) {
                return true;
            }
        }

        String qName = symbol.getQualifiedName();
        if (qName != null) {
            String lower = qName.toLowerCase();
            if (lower.contains("listener")
                    || lower.contains("handler")
                    || lower.contains("callback")
                    || lower.contains("hook")
                    || lower.contains("observer")
                    || lower.contains("subscriber")) {
                return true;
            }
        }

        return false;
    }

    private String resolveFilePath(CodeSymbol symbol) {
        String extData = symbol.getExtData();
        if (extData != null && extData.contains("filePath")) {
            return extData;
        }
        return null;
    }

    private DeadCodeReport.DeadCodeEntry buildEntry(CodeSymbol symbol) {
        DeadCodeReport.DeadCodeEntry entry = new DeadCodeReport.DeadCodeEntry();
        entry.setSymbolId(symbol.getId());
        entry.setQualifiedName(symbol.getQualifiedName());
        entry.setKind(symbol.getKind() != null ? symbol.getKind().name() : null);
        entry.setFilePath(resolveFilePath(symbol));
        return entry;
    }
}
