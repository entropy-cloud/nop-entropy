package io.nop.code.graph.heuristic;

import java.util.*;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.*;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.core.lang.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpringEventSynthesizer implements IHeuristicEdgeSynthesizer {
    private static final Logger LOG = LoggerFactory.getLogger(SpringEventSynthesizer.class);
    private static final String EVENT_LISTENER = "EventListener";
    private static final String PUBLISH_EVENT_METHOD = "publishEvent";

    @Override
    public String getSynthesizerId() {
        return "spring-event";
    }

    @Override
    public List<CodeMethodCall> synthesize(HeuristicContext context) {
        List<CodeMethodCall> synthesized = new ArrayList<>();
        SymbolTable symbolTable = context.getSymbolTable();

        Map<String, CodeSymbol> listenerByEventType = new LinkedHashMap<>();
        for (CodeSymbol sym : symbolTable.getAll()) {
            if (sym.getKind() == CodeSymbolKind.METHOD) {
                String extData = sym.getExtData();
                if (extData != null && extData.contains("annotations")) {
                    List<String> annotations = io.nop.code.core.util.ExtDataHelper.getAnnotations(extData);
                    if (annotations.contains(EVENT_LISTENER)
                            || annotations.contains("org.springframework.context.event.EventListener")) {
                        String eventType = extractListenerEventType(sym, symbolTable);
                        if (eventType != null) {
                            listenerByEventType.put(eventType, sym);
                        }
                    }
                }
            }
        }

        if (listenerByEventType.isEmpty()) {
            return synthesized;
        }

        Map<String, List<PublishPoint>> publishPoints = findPublishPoints(symbolTable, context.getCallGraph(), listenerByEventType.keySet());

        for (Map.Entry<String, List<PublishPoint>> entry : publishPoints.entrySet()) {
            String eventType = entry.getKey();
            CodeSymbol listener = listenerByEventType.get(eventType);
            if (listener == null) continue;

            for (PublishPoint pp : entry.getValue()) {
                CodeMethodCall synthetic = new CodeMethodCall();
                synthetic.setId(UUID.randomUUID().toString());
                synthetic.setCallerId(pp.publisherMethodId);
                synthetic.setCalleeId(listener.getId());
                synthetic.setCalleeQualifiedName(listener.getQualifiedName());
                synthetic.setMethodName(listener.getName());
                synthetic.setLine(-1);
                synthetic.setColumn(0);
                synthetic.setConfidence(EdgeConfidence.INFERRED);
                synthetic.setProvenance(EdgeProvenance.HEURISTIC);

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("synthesizedBy", getSynthesizerId());
                metadata.put("event", eventType);
                synthetic.setMetadata(JsonTool.stringify(metadata));

                synthesized.add(synthetic);
            }
        }

        LOG.debug("SpringEventSynthesizer produced {} edges", synthesized.size());
        return synthesized;
    }

    private String extractListenerEventType(CodeSymbol listenerMethod, SymbolTable symbolTable) {
        String returnType = listenerMethod.getReturnType();
        if (returnType != null && !returnType.equals("void") && !returnType.isEmpty()) {
            return returnType;
        }

        if (listenerMethod.getExtData() != null) {
            try {
                Object parsed = io.nop.core.lang.json.JsonTool.parseNonStrict(listenerMethod.getExtData());
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) parsed;
                    Object params = map.get("parameters");
                    if (params instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> paramList = (List<Object>) params;
                        if (!paramList.isEmpty()) {
                            Object first = paramList.get(0);
                            if (first instanceof String) return (String) first;
                            if (first instanceof Map) {
                                Object type = ((Map<String, Object>) first).get("type");
                                if (type != null) return type.toString();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.debug("Failed to extract event type from listener {}", listenerMethod.getQualifiedName(), e);
            }
        }

        return null;
    }

    private Map<String, List<PublishPoint>> findPublishPoints(SymbolTable symbolTable,
                                                               CallGraph callGraph,
                                                               Set<String> knownEventTypes) {
        Map<String, List<PublishPoint>> result = new LinkedHashMap<>();
        if (knownEventTypes.isEmpty()) return result;

        for (CodeSymbol sym : symbolTable.getAll()) {
            if (sym.getKind() != CodeSymbolKind.METHOD) continue;
            List<String> callees = callGraph.getCallees(sym.getId());
            for (String calleeId : callees) {
                CodeSymbol callee = symbolTable.getById(calleeId);
                if (callee != null && PUBLISH_EVENT_METHOD.equals(callee.getName())) {
                    for (String eventType : knownEventTypes) {
                        result.computeIfAbsent(eventType, k -> new ArrayList<>())
                                .add(new PublishPoint(sym.getId()));
                    }
                }
            }
        }

        return result;
    }

    private static class PublishPoint {
        final String publisherMethodId;

        PublishPoint(String publisherMethodId) {
            this.publisherMethodId = publisherMethodId;
        }
    }
}
