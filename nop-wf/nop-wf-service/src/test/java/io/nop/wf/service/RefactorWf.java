package io.nop.wf.service;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.File;

public class RefactorWf extends BaseTestCase {
    @Test
    public void refactorName() {
        File file = new File(getTestResourcesDir(), "_vfs/nop/test/wf");
        File[] files = file.listFiles();
        for (File subFile : files) {
            File dir = new File(subFile.getParentFile(), StringHelper.fileNameNoExt(subFile.getName()));
            File wfFile = new File(dir, "v1.xwf");
            XNode node = XNodeParser.instance().parseFromResource(new FileResource(wfFile));
            node.setTagName("workflow");
            node.setAttr("x:schema", "/nop/schema/wf/wf.xdef");
            node.setAttr("xmlns:x", "/nop/schema/xdsl.xdef");
            node.removeAttr("x:extended");

            refactorNode(node);

            node.saveToResource(new FileResource(wfFile), null);
        }
    }

    void refactorNode(XNode node) {
        XNode start = node.childByTag("start");
        start.renameAttr("startStepId", "startStepName");

        XNode steps = node.childByTag("steps");
        steps.forEachChild(step -> {
            step.renameAttr("id", "name");

            XNode transition = step.childByTag("transition");
            if (transition != null) {
                for (XNode to : transition.getChildren()) {
                    to.renameAttr("stepId", "stepName");
                }
            }

            XNode assignment = step.childByTag("assignment");
            if (assignment != null) {
                String selection = assignment.attrText("selection");
                if ("multipleSelect".equals(selection)) {
                    assignment.setAttr("selection", "multiple");
                } else if ("noSelect".equals(selection)) {
                    assignment.setAttr("selection", "auto");
                } else if ("singleSelect".equals(selection)) {
                    assignment.setAttr("selection", "single");
                } else if ("all".equals(selection)) {
                    assignment.setAttr("selection", "auto");
                }
                assignment.forEachChild(actor -> {
                    actor.renameAttr("id", "actorId");
                    actor.renameAttr("type", "actorType");
                });
                XNode actors = assignment.childByTag("actors");
                if (actors == null) {
                    actors = XNode.make("actors");
                    actors.appendChildren(assignment.detachChildren());
                    assignment.appendChild(actors);
                }
            }

            XNode refActions = step.childByTag("ref-actions");
            if (refActions != null) {
                refActions.forEachChild(refAction -> {
                    refAction.renameAttr("actionId", "name");
                });
            }
        });

        XNode actions = node.childByTag("actions");
        if (actions != null) {
            actions.forEachChild(action -> {
                action.renameAttr("id", "name");

                XNode transition = action.childByTag("transition");
                if (transition != null) {
                    for (XNode to : transition.getChildren()) {
                        to.renameAttr("stepId", "stepName");
                    }
                }
            });
        }
    }
}
