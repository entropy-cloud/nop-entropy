package io.nop.markdown.utils;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.markdown.IMarkdownTool;
import io.nop.markdown.simple.DefaultMarkdownTool;
import io.nop.markdown.model.MarkdownDocument;

@GlobalInstance
public class MarkdownTool {
    static IMarkdownTool s_instance = new DefaultMarkdownTool();

    public static void registerInstance(IMarkdownTool loader) {
        s_instance = loader;
    }

    public static IMarkdownTool instance() {
        return s_instance;
    }

    public static MarkdownDocument loadMarkdownTpl(String tplPath) {
        return (MarkdownDocument) ResourceComponentManager.instance().loadComponentModel(tplPath);
    }
}