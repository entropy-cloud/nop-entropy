package io.nop.code.graph.diff;

import java.util.*;
public class GraphDiffer {

    public GraphDiff diff(GraphSnapshot baseline, GraphSnapshot target) {
        GraphDiff diff = new GraphDiff();

        Set<String> addedNodes = new HashSet<>(target.getNodes());
        addedNodes.removeAll(baseline.getNodes());
        diff.setAddedNodes(addedNodes);

        Set<String> removedNodes = new HashSet<>(baseline.getNodes());
        removedNodes.removeAll(target.getNodes());
        diff.setRemovedNodes(removedNodes);

        Set<GraphSnapshot.EdgeKey> addedEdges = new HashSet<>(target.getEdges());
        addedEdges.removeAll(baseline.getEdges());
        diff.setAddedEdges(addedEdges);

        Set<GraphSnapshot.EdgeKey> removedEdges = new HashSet<>(baseline.getEdges());
        removedEdges.removeAll(target.getEdges());
        diff.setRemovedEdges(removedEdges);

        diff.setCommunityChanges(computeCommunityChanges(baseline, target));

        return diff;
    }

    private List<GraphDiff.CommunityChange> computeCommunityChanges(
            GraphSnapshot baseline, GraphSnapshot target) {
        List<GraphDiff.CommunityChange> changes = new ArrayList<>();

        Map<String, String> baseMap = baseline.getCommunityMap();
        Map<String, String> targetMap = target.getCommunityMap();

        Set<String> allNodes = new HashSet<>(baseMap.keySet());
        allNodes.addAll(targetMap.keySet());

        for (String nodeId : allNodes) {
            String oldComm = baseMap.get(nodeId);
            String newComm = targetMap.get(nodeId);

            if (oldComm != null && newComm != null && !oldComm.equals(newComm)) {
                GraphDiff.CommunityChange change = new GraphDiff.CommunityChange();
                change.setNodeId(nodeId);
                change.setOldCommunity(oldComm);
                change.setNewCommunity(newComm);
                changes.add(change);
            } else if (oldComm == null && newComm != null) {
                GraphDiff.CommunityChange change = new GraphDiff.CommunityChange();
                change.setNodeId(nodeId);
                change.setOldCommunity(null);
                change.setNewCommunity(newComm);
                changes.add(change);
            } else if (oldComm != null) {
                GraphDiff.CommunityChange change = new GraphDiff.CommunityChange();
                change.setNodeId(nodeId);
                change.setOldCommunity(oldComm);
                change.setNewCommunity(null);
                changes.add(change);
            }
        }

        return changes;
    }

    public static GraphSnapshot buildSnapshot(
            io.nop.code.core.graph.CallGraph callGraph,
            io.nop.code.graph.community.CommunityDetector.CommunityDetectionResult communities) {
        Set<String> nodes = new HashSet<>(callGraph.getAllNodeIds());

        Set<GraphSnapshot.EdgeKey> edges = new HashSet<>();
        for (String caller : callGraph.getAllNodeIds()) {
            for (String callee : callGraph.getCallees(caller)) {
                edges.add(new GraphSnapshot.EdgeKey(caller, callee));
            }
        }

        Map<String, String> communityMap = new HashMap<>();
        if (communities != null) {
            for (io.nop.code.graph.community.CommunityDetector.Community comm : communities.getCommunities()) {
                for (String symbolId : comm.getSymbolIds()) {
                    communityMap.put(symbolId, comm.getId());
                }
            }
        }

        return new GraphSnapshot(nodes, edges, communityMap);
    }
}
