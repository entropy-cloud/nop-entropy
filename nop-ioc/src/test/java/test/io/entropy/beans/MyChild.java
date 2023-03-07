/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package test.io.entropy.beans;

public class MyChild {

    private String name;
    private MyGrandparent myGrandparent;
    private MyParent myParent;

    public MyGrandparent getMyGrandparent() {
        return myGrandparent;
    }

    public void setMyGrandparent(MyGrandparent myGrandparent) {
        this.myGrandparent = myGrandparent;
    }

    public MyParent getMyParent() {
        return myParent;
    }

    public void setMyParent(MyParent myParent) {
        this.myParent = myParent;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
