package com.lms.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GraphUtilsTest {

    /**
     * The DSA prerequisite graph from the spec: Recursion -> Trees -> Graphs, Arrays -> Searching, Arrays -> Sorting.
     * Edge direction is prerequisite -> dependent.
     */
    private static Map<String, List<String>> dsaGraph() {
        Map<String, List<String>> g = new LinkedHashMap<>();
        g.put("Recursion", new ArrayList<>(List.of("Trees")));
        g.put("Trees", new ArrayList<>(List.of("Graphs")));
        g.put("Graphs", new ArrayList<>());
        g.put("Arrays", new ArrayList<>(List.of("Searching", "Sorting")));
        g.put("Searching", new ArrayList<>());
        g.put("Sorting", new ArrayList<>());
        return g;
    }

    @Test
    void topologicalSortGivesValidLearningOrderForDsaGraph() {
        List<String> order = GraphUtils.topologicalSort(dsaGraph());

        assertThat(order).containsExactly("Recursion", "Trees", "Graphs", "Arrays", "Searching", "Sorting");
        assertThat(order.indexOf("Recursion")).isLessThan(order.indexOf("Trees"));
        assertThat(order.indexOf("Trees")).isLessThan(order.indexOf("Graphs"));
        assertThat(order.indexOf("Arrays")).isLessThan(order.indexOf("Searching"));
        assertThat(order.indexOf("Arrays")).isLessThan(order.indexOf("Sorting"));
    }

    @Test
    void cycleIsDetectedAndRejected() {
        Map<String, List<String>> g = dsaGraph();
        g.get("Graphs").add("Recursion"); // Graphs -> Recursion closes Recursion -> Trees -> Graphs

        assertThat(GraphUtils.hasCycle(g)).isTrue();
        assertThat(GraphUtils.findCycle(g)).contains(List.of("Recursion", "Trees", "Graphs", "Recursion"));
        assertThatThrownBy(() -> GraphUtils.topologicalSort(g))
                .isInstanceOf(GraphUtils.CycleDetectedException.class)
                .hasMessageContaining("Recursion");
    }

    @Test
    void acyclicGraphHasNoCycle() {
        assertThat(GraphUtils.hasCycle(dsaGraph())).isFalse();
        assertThat(GraphUtils.findCycle(dsaGraph())).isEmpty();
    }

    @Test
    void selfLoopIsACycle() {
        Map<String, List<String>> g = new LinkedHashMap<>();
        g.put("A", List.of("A"));
        assertThat(GraphUtils.findCycle(g)).contains(List.of("A", "A"));
    }

    @Test
    void diamondIsNotACycle() {
        Map<String, List<String>> g = new LinkedHashMap<>();
        g.put("A", List.of("B", "C"));
        g.put("B", List.of("D"));
        g.put("C", List.of("D"));
        assertThat(GraphUtils.hasCycle(g)).isFalse();
        assertThat(GraphUtils.topologicalSort(g)).containsExactly("A", "B", "C", "D");
    }

    @Test
    void dfsAndBfsTraversal() {
        Map<String, List<String>> g = new LinkedHashMap<>();
        g.put("A", List.of("B", "C"));
        g.put("B", List.of("D"));
        g.put("C", List.of("E"));

        assertThat(GraphUtils.dfs(g, "A")).containsExactly("A", "B", "D", "C", "E");
        assertThat(GraphUtils.bfs(g, "A")).containsExactly("A", "B", "C", "D", "E");
        assertThat(GraphUtils.isReachable(g, "A", "E")).isTrue();
        assertThat(GraphUtils.isReachable(g, "E", "A")).isFalse();
    }
}
