package test.io.entropy.beans;

import java.util.Set;

/**
 * &lt;set&gt; 注入回归测试用 bean：未指定 set-class 时缺省实现必须是 Set 类型。
 */
public class MySetBean {
    private Set<String> tags;

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
}
