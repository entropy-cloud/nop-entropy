package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;

public class ManageTodoListExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "manage-todo-list";

    private static final Map<String, List<TodoItem>> todoLists = new ConcurrentHashMap<>();
    private static final String DEFAULT_LIST_KEY = "default";

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        return context.getExecutor().submit(() -> doExecute(call, context));
    }

    private AiToolCallResult doExecute(AiToolCall call, IToolExecuteContext context) {
        try {
            String action = call.attrText("action");

            if (action == null || action.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "action is required (read or write)");
            }

            String listKey = getListKey(context);

            if ("read".equals(action)) {
                return handleRead(call, listKey);
            } else if ("write".equals(action)) {
                return handleWrite(call, listKey);
            } else {
                return AiToolCallResult.errorResult(call.getId(), "Invalid action: " + action + ". Must be 'read' or 'write'");
            }
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e);
        }
    }

    private String getListKey(IToolExecuteContext context) {
        return DEFAULT_LIST_KEY;
    }

    private AiToolCallResult handleRead(AiToolCall call, String listKey) {
        List<TodoItem> todos = todoLists.getOrDefault(listKey, new ArrayList<>());

        StringBuilder sb = new StringBuilder();
        sb.append("Current todo list has ").append(todos.size()).append(" items:\n");

        for (int i = 0; i < todos.size(); i++) {
            TodoItem item = todos.get(i);
            sb.append(i + 1).append(". [").append(item.status).append("][")
                    .append(item.priority).append("] ").append(item.content).append("\n");
        }

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(sb.toString().trim());
        result.setOutput(output);
        return result;
    }

    private AiToolCallResult handleWrite(AiToolCall call, String listKey) {
        List<TodoItem> newTodos = parseTodos(call);
        List<TodoItem> oldTodos = todoLists.getOrDefault(listKey, new ArrayList<>());

        StringBuilder changesSummary = new StringBuilder();

        if (newTodos.isEmpty()) {
            todoLists.remove(listKey);
            changesSummary.append("Todo list cleared");
        } else {
            todoLists.put(listKey, newTodos);

            int completed = 0, inProgress = 0, pending = 0, cancelled = 0;
            for (TodoItem item : newTodos) {
                switch (item.status) {
                    case "completed": completed++; break;
                    case "in_progress": inProgress++; break;
                    case "pending": pending++; break;
                    case "cancelled": cancelled++; break;
                }
            }

            if (newTodos.size() != oldTodos.size()) {
                changesSummary.append("Created ").append(newTodos.size()).append(" todo items");
            } else {
                changesSummary.append("Updated: ");
                boolean first = true;
                for (int i = 0; i < newTodos.size(); i++) {
                    TodoItem newItem = newTodos.get(i);
                    TodoItem oldItem = i < oldTodos.size() ? oldTodos.get(i) : null;
                    if (oldItem == null || !oldItem.status.equals(newItem.status)) {
                        if (!first) changesSummary.append(", ");
                        first = false;
                        changesSummary.append(newItem.id).append(" ").append(newItem.status);
                    }
                }
                if (first) {
                    changesSummary.append("No changes");
                }
            }
        }

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(changesSummary.toString());
        result.setOutput(output);
        return result;
    }

    private List<TodoItem> parseTodos(AiToolCall call) {
        List<TodoItem> todos = new ArrayList<>();
        XNode node = call.getNode();
        if (node == null) return todos;

        XNode todosNode = node.childByTag("todos");
        if (todosNode == null) return todos;

        for (XNode todoNode : todosNode.getChildren()) {
            if ("todo".equals(todoNode.getTagName())) {
                String id = todoNode.attrText("id");
                String content = todoNode.attrText("content");
                String status = todoNode.attrText("status");
                String priority = todoNode.attrText("priority");

                if (id != null && content != null && status != null && priority != null) {
                    todos.add(new TodoItem(id, content, status, priority));
                }
            }
        }
        return todos;
    }

    private static class TodoItem {
        String id;
        String content;
        String status;
        String priority;

        TodoItem(String id, String content, String status, String priority) {
            this.id = id;
            this.content = content;
            this.status = status;
            this.priority = priority;
        }
    }
}
