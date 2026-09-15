package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class SkillExecutor implements IToolExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(SkillExecutor.class);

    public static final String TOOL_NAME = "skill";

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

    private static final int MAX_LOADED_FILE_CHARS = 4000;

    /**
     * Primary description file names whose content is inlined by the load
     * action (first match wins). Other files are listed by name only.
     */
    private static final List<String> DESCRIPTION_FILE_NAMES = List.of(
            "README.txt", "README.md", "SKILL.md", "skill.md", "description.txt");

    private AiToolCallResult handleLoad(AiToolCall call, IToolExecuteContext context, String skillName) {
        if (skillName == null || skillName.isEmpty()) {
            return AiToolCallResult.errorResult(call.getId(), "skillName is required for load action");
        }

        IResource skillDir;
        try {
            skillDir = VirtualFileSystem.instance().getResource("/nop/skills/" + skillName);
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(),
                    "Skill not found: " + skillName + " (VFS lookup failed: " + e + ")");
        }

        if (!skillDir.exists() || !skillDir.isDirectory()) {
            return AiToolCallResult.errorResult(call.getId(), "Skill not found: " + skillName);
        }

        String content = loadSkillContent(skillDir);
        if (content == null) {
            return AiToolCallResult.errorResult(call.getId(),
                    "Failed to load skill content: " + skillName);
        }

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody(content);
        result.setOutput(output);
        return result;
    }

    /**
     * Reads the real skill directory: lists every entry and inlines the
     * content of the first description file found (README/SKILL/description).
     * Returns null when the directory cannot be read at all.
     */
    private String loadSkillContent(IResource skillDir) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<skill name=\"").append(StringHelper.escapeXml(skillDir.getName())).append("\">\n");

            List<? extends IResource> children = VirtualFileSystem.instance().getChildren(skillDir.getPath());
            if (children == null || children.isEmpty()) {
                sb.append("  <files/>\n");
                sb.append("  <description>Skill directory is empty</description>\n");
                sb.append("</skill>");
                return sb.toString();
            }

            sb.append("  <files>\n");
            for (IResource child : children) {
                sb.append("    <file>").append(StringHelper.escapeXml(child.getName())).append("</file>\n");
            }
            sb.append("  </files>\n");

            String description = readDescriptionFile(skillDir);
            if (description != null) {
                sb.append("  <description>").append(StringHelper.escapeXml(description)).append("</description>\n");
            }

            sb.append("</skill>");
            return sb.toString();
        } catch (Exception e) {
            LOG.warn("nop.ai.skill.fail-load-content: failed to read skill content from VFS path {}",
                    skillDir.getPath(), e);
            return null;
        }
    }

    private String readDescriptionFile(IResource skillDir) {
        for (String fileName : DESCRIPTION_FILE_NAMES) {
            IResource candidate = VirtualFileSystem.instance().getResource(skillDir.getPath() + "/" + fileName);
            if (candidate != null && candidate.exists() && !candidate.isDirectory()) {
                try {
                    String text = candidate.readText();
                    if (text == null) {
                        return null;
                    }
                    return text.length() <= MAX_LOADED_FILE_CHARS
                            ? text
                            : text.substring(0, MAX_LOADED_FILE_CHARS)
                                    + "\n...[truncated " + (text.length() - MAX_LOADED_FILE_CHARS) + " chars]";
                } catch (Exception e) {
                    LOG.warn("nop.ai.skill.fail-read-description: failed to read description file {} of skill {}",
                            fileName, skillDir.getName(), e);
                    return null;
                }
            }
        }
        return null;
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
            LOG.warn("nop.ai.skill.fail-discover-skills: failed to discover skills from VFS /nop/skills", e);
            return skills;
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
