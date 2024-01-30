package io.nop.rpc.model;

import java.util.List;

public interface IWithOptions {
    String getDescription();
    List<ApiOptionModel> getOptions();
}
