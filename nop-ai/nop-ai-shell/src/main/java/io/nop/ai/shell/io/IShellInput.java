package io.nop.ai.shell.io;

import io.nop.commons.util.CollectionHelper;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Shell 输入接口
 */
public interface IShellInput extends Closeable {

    String readLine();

    Iterator<String> lines();

    default List<String> readAllLines() {
        List<String> list = new ArrayList<>();
        Iterator<String> it = lines();
        while(it.hasNext()){
            String line = it.next();
            if(line == null)
                break;
            list.add(line);
        }
        return list;
    }


    void close();

    boolean isClosed();
}
