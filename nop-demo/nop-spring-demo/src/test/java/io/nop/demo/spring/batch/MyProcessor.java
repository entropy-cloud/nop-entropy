package io.nop.demo.spring.batch;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Service;


@Service
public class MyProcessor implements ItemProcessor {
    @Override
    public Object process(Object o) throws Exception {
        return null;
    }
}
