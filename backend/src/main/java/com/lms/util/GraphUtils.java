package com.lms.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Directed-graph algorithms over an adjacency map {@code node -> successors}.
 *
 * <p>For topic prerequisites the edge direction is {@code prerequisite -> dependent topic}
 * ("Recursion must come before Trees"), so a topological order is a valid learning order.
 *
 * <p>Nodes that only appear as successors are treated as nodes with no outgoing edges.
 * Where several nodes are equally valid, results follow the map's iteration order, so pass a
 * {@link LinkedHashMap} for deterministic output.
 */
public final class GraphUtils {

    private GraphUtils() {
    }

    /** Depth-first (pre-order) traversal from {@code start}. Iterative, so deep graphs cannot overflow the stack. */
    public static <T> List<T> dfs(Map<T, ? extends Collection<T>> graph, T start) {
        List<T> order = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        Deque<T> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            if (!visited.add(node)) {
                continue;
            }
            order.add(node);
            List<T> next = new ArrayList<>(successors(graph, node));
            Collections.reverse(next); // so the first successor is visited first
            for (T n : next) {
                if (!visited.contains(n)) {
                    stack.push(n);
                }
            }
        }
        return order;
    }

    /** Breadth-first traversal from {@code start}. */
    public static <T> List<T> bfs(Map<T, ? extends Collection<T>> graph, T start) {
        List<T> order = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        Deque<T> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            T node = queue.poll();
            order.add(node);
            for (T n : successors(graph, node)) {
                if (visited.add(n)) {
                    queue.add(n);
                }
            }
        }
        return order;
    }

    /**
     * Kahn's algorithm. Among nodes that are ready at the same time, the one that appears earliest
     * in the graph's iteration order is emitted first.
     *
     * @throws CycleDetectedException if the graph contains a cycle
     */
    public static <T> List<T> topologicalSort(Map<T, ? extends Collection<T>> graph) {
        Map<T, Integer> index = indexNodes(graph);
        Map<T, Integer> inDegree = new HashMap<>();
        index.keySet().forEach(n -> inDegree.put(n, 0));
        for (T node : index.keySet()) {
            for (T n : successors(graph, node)) {
                inDegree.merge(n, 1, Integer::sum);
            }
        }

        PriorityQueue<T> ready = new PriorityQueue<>((a, b) -> Integer.compare(index.get(a), index.get(b)));
        inDegree.forEach((n, d) -> {
            if (d == 0) {
                ready.add(n);
            }
        });

        List<T> order = new ArrayList<>(index.size());
        while (!ready.isEmpty()) {
            T node = ready.poll();
            order.add(node);
            for (T n : successors(graph, node)) {
                if (inDegree.merge(n, -1, Integer::sum) == 0) {
                    ready.add(n);
                }
            }
        }

        if (order.size() != index.size()) {
            throw new CycleDetectedException(findCycle(graph).orElse(List.of()));
        }
        return order;
    }

    public static <T> boolean hasCycle(Map<T, ? extends Collection<T>> graph) {
        return findCycle(graph).isPresent();
    }

    /**
     * Returns one cycle as a closed path (first node repeated at the end, e.g. [A, B, C, A]),
     * or empty if the graph is acyclic. Uses iterative three-colour DFS.
     */
    public static <T> Optional<List<T>> findCycle(Map<T, ? extends Collection<T>> graph) {
        Set<T> nodes = indexNodes(graph).keySet();
        Set<T> done = new HashSet<>();
        for (T root : nodes) {
            if (done.contains(root)) {
                continue;
            }
            // path holds the current DFS stack (grey nodes) in order; iterators hold each frame's progress
            LinkedHashSet<T> path = new LinkedHashSet<>();
            Deque<T> frames = new ArrayDeque<>();
            Deque<Iterator<T>> iterators = new ArrayDeque<>();
            path.add(root);
            frames.push(root);
            iterators.push(successors(graph, root).iterator());

            while (!frames.isEmpty()) {
                Iterator<T> it = iterators.peek();
                if (it.hasNext()) {
                    T next = it.next();
                    if (path.contains(next)) {
                        List<T> cycle = new ArrayList<>();
                        boolean inCycle = false;
                        for (T p : path) {
                            inCycle |= p.equals(next);
                            if (inCycle) {
                                cycle.add(p);
                            }
                        }
                        cycle.add(next);
                        return Optional.of(cycle);
                    }
                    if (!done.contains(next)) {
                        path.add(next);
                        frames.push(next);
                        iterators.push(successors(graph, next).iterator());
                    }
                } else {
                    T finished = frames.pop();
                    iterators.pop();
                    path.remove(finished);
                    done.add(finished);
                }
            }
        }
        return Optional.empty();
    }

    /** True if {@code target} is reachable from {@code source} (a node reaches itself). */
    public static <T> boolean isReachable(Map<T, ? extends Collection<T>> graph, T source, T target) {
        return source.equals(target) || bfs(graph, source).contains(target);
    }

    private static <T> Collection<T> successors(Map<T, ? extends Collection<T>> graph, T node) {
        Collection<T> next = graph.get(node);
        return next == null ? List.of() : next;
    }

    /** All nodes (keys first, then successor-only nodes) mapped to their first-seen position. */
    private static <T> Map<T, Integer> indexNodes(Map<T, ? extends Collection<T>> graph) {
        Map<T, Integer> index = new LinkedHashMap<>();
        for (T key : graph.keySet()) {
            index.putIfAbsent(key, index.size());
        }
        for (Collection<T> next : graph.values()) {
            for (T n : next) {
                index.putIfAbsent(n, index.size());
            }
        }
        return index;
    }

    /** Thrown by {@link #topologicalSort} when the graph is not a DAG. */
    public static class CycleDetectedException extends RuntimeException {

        private final transient List<?> cycle;

        public CycleDetectedException(List<?> cycle) {
            super("Graph contains a cycle: " + cycle);
            this.cycle = cycle;
        }

        public List<?> getCycle() {
            return cycle;
        }
    }
}
