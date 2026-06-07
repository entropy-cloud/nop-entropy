package io.nop.code.graph.heuristic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.model.EdgeProvenance;
import io.nop.code.core.model.HeuristicContext;
import io.nop.code.core.model.IHeuristicEdgeSynthesizer;
import io.nop.code.core.model.CodeMethodCall;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterfaceImplSynthesizer implements IHeuristicEdgeSynthesizer {
    private static final Logger LOG = LoggerFactory.getLogger(InterfaceImplSynthesizer.class);

    @Override
    public String getSynthesizerId() {
        return "interface-impl";
    }

    @Override
    public List<CodeMethodCall> synthesize(HeuristicContext context) {
        List<CodeMethodCall> synthesized = new ArrayList<>();
        SymbolTable symbolTable = context.getSymbolTable();
        Map<String, Set<String>> inheritanceIndex = context.getInheritanceIndex();
        CallGraph callGraph = context.getCallGraph();

        for (Map.Entry<String, List<String>> entry : callGraph.getForwardMap().entrySet()) {
            String callerId = entry.getKey();
            for (String calleeId : entry.getValue()) {
                CodeSymbol calleeSymbol = symbolTable.getById(calleeId);
                if (calleeSymbol == null) continue;

                if (calleeSymbol.getKind() == CodeSymbolKind.METHOD
                        && calleeSymbol.getDeclaringSymbolId() != null) {
                    CodeSymbol declaringType = symbolTable.getById(calleeSymbol.getDeclaringSymbolId());
                    if (declaringType != null && declaringType.getKind() == CodeSymbolKind.INTERFACE) {
                        String interfaceQName = declaringType.getQualifiedName();
                        Set<String> implIds = inheritanceIndex.get(interfaceQName);
                        if (implIds == null || implIds.isEmpty()) continue;

                        for (String implTypeId : implIds) {
                            CodeSymbol implType = symbolTable.getById(implTypeId);
                            if (implType == null) continue;

                            String implMethodPrefix = implType.getQualifiedName()
                                    + "." + calleeSymbol.getName();
                            List<CodeSymbol> implMethods = symbolTable.findAllByQualifiedNamePrefix(implMethodPrefix);
                            for (CodeSymbol implMethod : implMethods) {
                                CodeMethodCall synthetic = new CodeMethodCall();
                                synthetic.setId(UUID.randomUUID().toString());
                                synthetic.setCallerId(callerId);
                                synthetic.setCalleeId(implMethod.getId());
                                synthetic.setCalleeQualifiedName(implMethod.getQualifiedName());
                                synthetic.setMethodName(calleeSymbol.getName());
                                synthetic.setLine(-1);
                                synthetic.setColumn(0);
                                synthetic.setConfidence(EdgeConfidence.INFERRED);
                                synthetic.setProvenance(EdgeProvenance.HEURISTIC);

                                Map<String, Object> metadata = new LinkedHashMap<>();
                                metadata.put("synthesizedBy", getSynthesizerId());
                                metadata.put("via", interfaceQName + "." + calleeSymbol.getName());
                                synthetic.setMetadata(JsonTool.stringify(metadata));

                                synthesized.add(synthetic);
                            }
                        }
                    }
                }
            }
        }

        LOG.debug("InterfaceImplSynthesizer produced {} edges", synthesized.size());
        return synthesized;
    }
}
