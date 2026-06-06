package io.nop.code.graph.heuristic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

        Map<String, List<CodeSymbol>> listenerByEventType = new LinkedHashMap<>();
        for (CodeSymbol sym : symbolTable.getAll()) {
            if (sym.getKind() == CodeSymbolKind.METHOD) {
                String extData = sym.getExtData();
                if (extData != null && extData.contains("annotations")) {
                    List<String> annotations = io.nop.code.core.util.ExtDataHelper.getAnnotations(extData);
                    if (annotations.contains(EVENT_LISTENER)
                            || annotations.contains("org.springframework.context.event.EventListener")) {
                        String eventType = extractListenerEventType(sym, symbolTable);
                        if (eventType != null) {
                            listenerByEventType.computeIfAbsent(eventType, k -> new ArrayList<>()).add(sym);
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
            List<CodeSymbol> listeners = listenerByEventType.get(eventType);
            if (listeners == null || listeners.isEmpty()) continue;

            for (PublishPoint pp : entry.getValue()) {
                for (CodeSymbol listener : listeners) {
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

        Set<String> knownShortNames = new HashSet<>();
        for (String eventType : knownEventTypes) {
            int dot = eventType.lastIndexOf('.');
            knownShortNames.add(dot >= 0 ? eventType.substring(dot + 1) : eventType);
        }

        for (CodeSymbol sym : symbolTable.getAll()) {
            if (sym.getKind() != CodeSymbolKind.METHOD) continue;
            List<String> callees = callGraph.getCallees(sym.getId());
            for (String calleeId : callees) {
                CodeSymbol callee = symbolTable.getById(calleeId);
                if (callee != null && PUBLISH_EVENT_METHOD.equals(callee.getName())) {
                    String matchedEventType = matchPublisherEventType(sym, callGraph, symbolTable, knownEventTypes, knownShortNames);
                    if (matchedEventType != null) {
                        result.computeIfAbsent(matchedEventType, k -> new ArrayList<>())
                                .add(new PublishPoint(sym.getId()));
                    }
                }
            }
        }

        return result;
    }

    private String matchPublisherEventType(CodeSymbol publisherMethod, CallGraph callGraph,
                                             SymbolTable symbolTable, Set<String> knownEventTypes,
                                             Set<String> knownShortNames) {
        List<String> callees = callGraph.getCallees(publisherMethod.getId());
        for (String calleeId : callees) {
            CodeSymbol callee = symbolTable.getById(calleeId);
            if (callee != null && PUBLISH_EVENT_METHOD.equals(callee.getName())) {
                String callerLine = extractPublishEventArgType(publisherMethod, callee, symbolTable);
                if (callerLine != null) {
                    for (String eventType : knownEventTypes) {
                        if (eventType.equals(callerLine) || eventType.endsWith("." + callerLine)) {
                            return eventType;
                        }
                    }
                    for (String shortName : knownShortNames) {
                        if (shortName.equals(callerLine)) {
                            for (String eventType : knownEventTypes) {
                                if (eventType.endsWith("." + shortName) || eventType.equals(shortName)) {
                                    return eventType;
                                }
                            }
                        }
                    }
                }
            }
        }

        String methodName = publisherMethod.getName();
        for (String eventType : knownEventTypes) {
            String eventShort = eventType.contains(".") ? eventType.substring(eventType.lastIndexOf('.') + 1) : eventType;
            String eventBase = eventShort.replace("Event", "");
            if (!eventBase.isEmpty() && methodName.toLowerCase().contains(eventBase.toLowerCase())) {
                return eventType;
            }
        }

        return null;
    }

    private String extractPublishEventArgType(CodeSymbol publisher, CodeSymbol publishMethod,
                                                SymbolTable symbolTable) {
        if (publisher.getExtData() != null) {
            try {
                Object parsed = JsonTool.parseNonStrict(publisher.getExtData());
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) parsed;
                    Object params = map.get("publishEventTypes");
                    if (params instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> paramList = (List<Object>) params;
                        if (!paramList.isEmpty()) {
                            Object first = paramList.get(0);
                            if (first instanceof String) return (String) first;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.debug("Failed to extract publish event arg type from {}", publisher.getQualifiedName(), e);
            }
        }

        return null;
    }

    private static class PublishPoint {
        final String publisherMethodId;

        PublishPoint(String publisherMethodId) {
            this.publisherMethodId = publisherMethodId;
        }
    }
}
