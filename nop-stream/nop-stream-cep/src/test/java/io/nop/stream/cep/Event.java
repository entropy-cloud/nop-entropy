/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Objects;

/**
 * R6 plan 2323 follow-up: this fixture enters checkpointed keyed state in the
 * CEP checkpoint/restore E2Es, so it must satisfy the platform serialization
 * contract — only {@code @DataBean} classes participate in JSON checkpointing
 * (the same rule the R5 freezeRawValue and the R6 payload-leaf freeze enforce
 * at snapshot time). Without the annotation the snapshot-time defensive copy
 * fails fast with only-data-bean (previously these in-memory E2Es never
 * triggered the JSON persist path, so the gap stayed invisible).
 */
@DataBean
public class Event {
    private int id;
    private String name;

    public Event(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public String toString() {
        return "Event[id=" + id + ",name=" + name + "]";
    }

    public Event() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return id == event.id && Objects.equals(name, event.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}