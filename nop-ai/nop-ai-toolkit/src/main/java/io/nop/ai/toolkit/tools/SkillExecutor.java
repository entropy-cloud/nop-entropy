package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.IResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;

public class SkillExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "skill";

    private static final Map<String, List<String>> loadedSkills = new ConcurrentHashMap<>();

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
            String skillName = call.attrText("skillName");

            if (action == null || action.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "action is required (list or load)");
            }

            if ("list".equals(action)) {
                return handleList(call, context);
            } else if ("load".equals(action)) {
                return handleLoad(call, context, skillName);
            } else {
                return AiToolCallResult.errorResult(call.getId(), "Invalid action: " + action + ". Must be 'list' or 'load'");
            }
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e);
        }
    }

    private AiToolCallResult handleList(AiToolCall call, IToolExecuteContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("<skills>\n");

        List<SkillInfo> skills = discoverSkills(context);
        for (SkillInfo skill : skills) {
            sb.append("  <skill name=\"").append(StringHelper.escapeXml(skill.name)).append("\">")
                    .append(StringHelper.escapeXml(skill.description))
                    .append("</skill>\n");
        }

        sb.append("</skills>");

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(sb.toString());
        result.setOutput(output);
        return result;
    }

    private AiToolCallResult handleLoad(AiToolCall call, IToolExecuteContext context, String skillName) {
        if (skillName == null || skillName.isEmpty()) {
            return AiToolCallResult.errorResult(call.getId(), "skillName is required for load action");
        }

        List<SkillInfo> skills = discoverSkills(context);
        boolean found = false;
        for (SkillInfo skill : skills) {
            if (skill.name.equals(skillName)) {
                found = true;
                break;
            }
        }

        if (!found) {
            return AiToolCallResult.errorResult(call.getId(), "Skill not found: " + skillName);
        }

        String contextKey = getContextKey(context);
        loadedSkills.computeIfAbsent(contextKey, k -> new ArrayList<>()).add(skillName);

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody("Skill '" + skillName + "' loaded successfully.");
        result.setOutput(output);
        return result;
    }

    private String getContextKey(IToolExecuteContext context) {
        return "default";
    }

    private List<SkillInfo> discoverSkills(IToolExecuteContext context) {
        List<SkillInfo> skills = new ArrayList<>();

        try {
            IResource skillsDir = VirtualFileSystem.instance().getResource("/nop/skills");
            if (skillsDir.exists() && skillsDir.isDirectory()) {
                for (IResource child : VirtualFileSystem.instance().getChildren(skillsDir.getPath())) {
                    if (child.isDirectory()) {
                        String name = child.getName();
                        String description = "Skill: " + name;
                        skills.add(new SkillInfo(name, description));
                    }
                }
            }
        } catch (Exception e) {
        }

        if (skills.isEmpty()) {
            skills.add(new SkillInfo("log-analysis", "Analyze log files for errors and patterns"));
            skills.add(new SkillInfo("translator", "Translate text between languages"));
            skills.add(new SkillInfo("calculator", "Perform mathematical calculations"));
            skills.add(new SkillInfo("code-review", "Review code for quality and best practices"));
            skills.add(new SkillInfo("test-generator", "Generate unit tests for code"));
        }

        return skills;
    }

    private static class SkillInfo {
        String name;
        String description;

        SkillInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
