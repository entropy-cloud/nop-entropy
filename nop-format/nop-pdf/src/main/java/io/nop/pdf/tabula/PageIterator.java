package io.nop.pdf.tabula;

import io.nop.api.core.exceptions.NopException;

import java.io.IOException;
import java.util.Iterator;

public class PageIterator implements Iterator<Page> {

    private ObjectExtractor objectExtractor;
    private Iterator<Integer> pageIndexIterator;

    public PageIterator(ObjectExtractor objectExtractor, Iterable<Integer> pages) {
        super();
        this.objectExtractor = objectExtractor;
        this.pageIndexIterator = pages.iterator();
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    @Override
    public boolean hasNext() {
        return pageIndexIterator.hasNext();
    }

    @Override
    public Page next() {
        if (!this.hasNext()) {
            throw new IllegalStateException();
        }
        try {
            return objectExtractor.extractPage(pageIndexIterator.next());
        } catch (IOException e) {
            // 页提取失败不能静默返回null元素，调用方解引用会NPE且丢失异常上下文
            throw NopException.adapt(e);
        }
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

}
