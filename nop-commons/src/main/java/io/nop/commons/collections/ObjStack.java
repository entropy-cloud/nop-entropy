package io.nop.commons.collections;

import java.util.ArrayList;
import java.util.List;

public class ObjStack {
    private List<Object> stack = new ArrayList<>();

    public void push(Object obj) {
        stack.add(obj);
    }

    public Object pop() {
        return stack.remove(stack.size() - 1);
    }

    public Object getCurrent() {
        if (stack.isEmpty())
            return null;
        return stack.get(stack.size() - 1);
    }

    public Object getParent() {
        if (stack.size() <= 1)
            return null;

        return stack.get(stack.size() - 2);
    }

    public Object getParentParent() {
        if (stack.size() <= 2)
            return null;

        return stack.get(stack.size() - 3);
    }

    public Object getRoot() {
        if (stack.isEmpty())
            return null;
        return stack.get(0);
    }

    public void clear() {
        stack.clear();
    }

    public Object get(int index) {
        return stack.get(index);
    }

    public int size() {
        return stack.size();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
