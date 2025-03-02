package io.nop.http.apache;


import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;

public interface IStreamResponseConsumer {
    void onStreamBegin(HttpResponse response, ContentType contentType);
}
