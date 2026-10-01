package com.example.ui.diagram;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feeds the bpmn-js viewer: which BPMN elements are done, which are currently
 * running, and - for human tasks - who owns each running one.
 */
@RestController
public class ProcessDiagramController {

    /** Task states that still need somebody to act. */
    private static final Set<String> OPEN_TASK_STATES = Set.of("Ready", "Reserved");

    private static final String HUMAN_TASK = "HumanTaskNode";

    private final DataIndexClient dataIndex;

    ProcessDiagramController(DataIndexClient dataIndex) {
        this.dataIndex = dataIndex;
    }

    @GetMapping("/processes/{processInstanceId}/diagram-state")
    public DiagramState diagramState(@PathVariable String processInstanceId,
                                     @AuthenticationPrincipal OidcUser principal) {

        DataIndexClient.Snapshot snapshot = dataIndex.snapshot(processInstanceId);
        if (snapshot.isEmpty()) {
            return new DiagramState(processInstanceId, null, "UNKNOWN", List.of(), List.of());
        }

        String username = principal == null ? null : principal.getPreferredUsername();
        List<String> myGroups = principal == null ? List.of() : groupsOf(principal);

        // A node can be entered more than once (loops, or parallel branches), so
        // collapse by BPMN element id. Active always wins over completed.
        Set<String> activeIds = new LinkedHashSet<>();
        Set<String> completedIds = new LinkedHashSet<>();
        for (DataIndexClient.NodeInstance node : snapshot.nodes()) {
            (node.active() ? activeIds : completedIds).add(node.definitionId());
        }
        completedIds.removeAll(activeIds);

        // Open tasks, bucketed by task name. A user task node and its task share
        // the same name, which is the only join Data Index gives us.
        Map<String, Deque<DataIndexClient.TaskInstance>> openTasksByName = new HashMap<>();
        for (DataIndexClient.TaskInstance task : snapshot.tasks()) {
            if (OPEN_TASK_STATES.contains(task.state())) {
                openTasksByName.computeIfAbsent(task.name(), k -> new ArrayDeque<>()).add(task);
            }
        }

        List<DataIndexClient.NodeInstance> activeNodes = new ArrayList<>(
                snapshot.nodes().stream().filter(DataIndexClient.NodeInstance::active).toList());
        activeNodes.sort(Comparator.comparing(DataIndexClient.NodeInstance::enter,
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<ActiveNode> active = new ArrayList<>();
        for (DataIndexClient.NodeInstance node : activeNodes) {
            DataIndexClient.TaskInstance task = null;
            if (HUMAN_TASK.equals(node.type())) {
                Deque<DataIndexClient.TaskInstance> queue = openTasksByName.get(node.name());
                if (queue != null) {
                    task = queue.poll();
                }
            }
            active.add(toActiveNode(node, task, username, myGroups));
        }

        return new DiagramState(processInstanceId,
                snapshot.processId(),
                snapshot.state(),
                List.copyOf(completedIds),
                active);
    }

    private static ActiveNode toActiveNode(DataIndexClient.NodeInstance node,
                                           DataIndexClient.TaskInstance task,
                                           String username,
                                           List<String> myGroups) {
        if (task == null) {
            return new ActiveNode(node.definitionId(), node.name(), node.type(),
                    null, null, null, List.of(), false);
        }
        List<String> groups = task.potentialGroups() == null ? List.of() : task.potentialGroups();
        boolean claimedByMe = Objects.equals(username, task.actualOwner());
        boolean claimableByMe = task.actualOwner() == null
                && (!Collections.disjoint(groups, myGroups)
                    || (task.potentialUsers() != null && task.potentialUsers().contains(username)));

        return new ActiveNode(node.definitionId(), node.name(), node.type(),
                task.id(), task.state(), task.actualOwner(), groups,
                claimedByMe || claimableByMe);
    }

    @SuppressWarnings("unchecked")
    private static List<String> groupsOf(OidcUser principal) {
        Object claim = principal.getClaim("groups");
        return claim instanceof List<?> list ? (List<String>) list : List.of();
    }

    /**
     * @param completed BPMN element ids that ran and finished
     * @param active    BPMN element ids currently running, with task detail where applicable
     */
    public record DiagramState(String processInstanceId,
                               String processId,
                               String state,
                               List<String> completed,
                               List<ActiveNode> active) {
    }

    /**
     * @param mine true when the signed-in user can act on this node right now -
     *             either they already own the task, or it is unclaimed and offered
     *             to one of their groups
     */
    public record ActiveNode(String elementId,
                             String name,
                             String type,
                             String taskId,
                             String status,
                             String owner,
                             List<String> groups,
                             boolean mine) {
    }
}
