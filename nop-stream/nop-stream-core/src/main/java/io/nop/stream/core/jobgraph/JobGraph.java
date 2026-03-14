/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the execution plan as a Directed Acyclic Graph (DAG).
 * This is the core data structure that captures the execution topology of a
 * streaming job, optimized from StreamGraph for actual execution.
 *
 * <p>The JobGraph maintains two main collections:
 * <ul>
 *   <li>Job vertices: Represent executable tasks (operators, sources, sinks)</li>
 *   <li>Job edges: Represent data exchange between tasks</li>
 * </ul>
 *
 * <p>JobGraph is the final representation before job execution. It is created
 * by optimizing and translating a StreamGraph into an executable form. Each
 * JobVertex represents a task that can be scheduled and executed, while
 * JobEdge represents the data flow and partitioning strategy between tasks.
 *
 * <p>This class is inspired by Apache Flink's JobGraph design but simplified
 * for nop-stream's requirements. It serves as the bridge between the logical
 * execution plan (StreamGraph) and the physical execution layer.
 *
 * <p>Key differences from StreamGraph:
 * <ul>
 *   <li>Vertices are identified by String IDs instead of Integer IDs</li>
 *   <li>Represents optimized execution plan, not logical topology</li>
 *   <li>Each vertex corresponds to a schedulable task</li>
 *   <li>Edges contain execution-time partitioning and deployment info</li>
 * </ul>
 *
 * @see JobVertex
 * @see JobEdge
 */
public class JobGraph implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The name of this streaming job.
     */
    private final String jobName;

    /**
     * Map of all job vertices in the graph, keyed by vertex ID.
     * Each vertex represents an executable task in the job.
     */
    private final Map<String, JobVertex> vertices;

    /**
     * List of all job edges in the graph.
     * Each edge represents data flow between two vertices.
     */
    private final List<JobEdge> edges;

    /**
     * Creates a new empty JobGraph with the specified name.
     *
     * @param jobName the name of the streaming job
     * @throws IllegalArgumentException if jobName is null
     */
    public JobGraph(String jobName) {
        if (jobName == null) {
            throw new IllegalArgumentException("Job name cannot be null");
        }
        this.jobName = jobName;
        this.vertices = new HashMap<>();
        this.edges = new ArrayList<>();
    }

    /**
     * Gets the name of this streaming job.
     *
     * @return the job name
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * Adds a job vertex to the graph.
     *
     * @param vertex the job vertex to add
     * @throws IllegalArgumentException if vertex is null or a vertex with the same ID already exists
     */
    public void addVertex(JobVertex vertex) {
        if (vertex == null) {
            throw new IllegalArgumentException("JobVertex cannot be null");
        }

        String vertexId = vertex.getId();
        if (vertices.containsKey(vertexId)) {
            throw new IllegalArgumentException(
                "JobVertex with ID " + vertexId + " already exists in the graph"
            );
        }

        vertices.put(vertexId, vertex);
    }

    /**
     * Adds a job edge to the graph.
     * The edge represents data flow from source to target vertex.
     *
     * @param edge the job edge to add
     * @throws IllegalArgumentException if edge is null, or references non-existent vertices
     */
    public void addEdge(JobEdge edge) {
        if (edge == null) {
            throw new IllegalArgumentException("JobEdge cannot be null");
        }

        String sourceId = edge.getSourceVertex();
        String targetId = edge.getTargetVertex();

        if (!vertices.containsKey(sourceId)) {
            throw new IllegalArgumentException(
                "Source vertex with ID " + sourceId + " does not exist in the graph"
            );
        }

        if (!vertices.containsKey(targetId)) {
            throw new IllegalArgumentException(
                "Target vertex with ID " + targetId + " does not exist in the graph"
            );
        }

        edges.add(edge);
    }

    /**
     * Gets a job vertex by its ID.
     *
     * @param vertexId the ID of the vertex to retrieve
     * @return the job vertex, or null if not found
     */
    public JobVertex getVertex(String vertexId) {
        return vertices.get(vertexId);
    }

    /**
     * Gets all job vertices in the graph.
     * Returns an unmodifiable view of the vertices map.
     *
     * @return unmodifiable map of vertex ID to vertex
     */
    public Map<String, JobVertex> getVertices() {
        return Collections.unmodifiableMap(vertices);
    }

    /**
     * Gets all job edges in the graph.
     * Returns an unmodifiable view of the edges list.
     *
     * @return unmodifiable list of all edges
     */
    public List<JobEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * Gets the number of vertices in the graph.
     *
     * @return the number of vertices
     */
    public int getNumberOfVertices() {
        return vertices.size();
    }

    /**
     * Gets the number of edges in the graph.
     *
     * @return the number of edges
     */
    public int getNumberOfEdges() {
        return edges.size();
    }

    /**
     * Checks if the graph contains a vertex with the specified ID.
     *
     * @param vertexId the ID to check
     * @return true if the vertex exists, false otherwise
     */
    public boolean containsVertex(String vertexId) {
        return vertices.containsKey(vertexId);
    }

    /**
     * Clears all vertices and edges from the graph.
     */
    public void clear() {
        vertices.clear();
        edges.clear();
    }

    @Override
    public String toString() {
        return "JobGraph{" +
            "jobName='" + jobName + '\'' +
            ", vertices=" + vertices.size() +
            ", edges=" + edges.size() +
            '}';
    }
}
