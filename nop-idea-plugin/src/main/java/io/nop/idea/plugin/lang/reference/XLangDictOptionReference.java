package io.nop.idea.plugin.lang.reference;

import java.util.function.Function;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.core.dict.DictProvider;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.resource.EnumDictOptionBean;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;

/**
 * 对字典选项的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-12
 */
public class XLangDictOptionReference extends XLangReferenceBase {
    private final String dictName;
    private final Object dictOptionValue;

    private DictBean dictBean;

    public XLangDictOptionReference(
            PsiElement myElement, TextRange myRangeInElement, //
            String dictName
    ) {
        this(myElement, myRangeInElement, dictName, null);
    }

    public XLangDictOptionReference(
            PsiElement myElement, TextRange myRangeInElement, //
            String dictName, Object dictOptionValue
    ) {
        super(myElement, myRangeInElement);
        this.dictName = dictName;
        this.dictOptionValue = dictOptionValue;
    }

    public DictBean getDictBean() {
        if (dictBean == null) {
            dictBean = ProjectEnv.withProject(getElement().getProject(),
                                              () -> DictProvider.instance().getDict(null, dictName, null, null));
        }
        return dictBean;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        DictBean dictBean = getDictBean();
        if (dictBean == null) {
            return null;
        }

        DictOptionBean dictOpt = dictBean.getOptionByValue(dictOptionValue);
        if (dictOpt instanceof EnumDictOptionBean opt) {
            return opt.target;
        } else {
            Function<PsiFile, PsiElement> targetResolver = //
                    (file) -> XmlPsiHelper.findFirstElement(file, (element) -> {
                        if (element instanceof LeafPsiElement value //
                            && dictOptionValue.equals(value.getText()) //
                        ) {
                            PsiElement parent = //
                                    PsiTreeUtil.getParentOfType(element, YAMLKeyValue.class);
                            PsiElement key = parent != null ? parent.getFirstChild() : null;

                            return key != null && "value".equals(key.getText());
                        }
                        return false;
                    });

            Project project = getElement().getProject();
            String path = "/dict/" + dictName + ".dict.yaml";

            NopVirtualFile target = new NopVirtualFile(project, path, dictOptionValue != null ? targetResolver : null);

            if (target.hasEmptyChildren()) {
                String msg = dictOptionValue != null
                             //
                             ? NopPluginBundle.message("xlang.annotation.reference.dict-option-not-defined",
                                                       dictOptionValue,
                                                       path)
                             : NopPluginBundle.message("xlang.annotation.reference.dict-yaml-not-found", path);
                setMessage(msg);
            }
            return target;
        }
    }
}
