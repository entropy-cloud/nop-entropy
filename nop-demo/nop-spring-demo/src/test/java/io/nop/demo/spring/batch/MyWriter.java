package io.nop.demo.spring.batch;

import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MyWriter implements ItemWriter {
    @Override
    public void write(List list) throws Exception {
        
    }
}
