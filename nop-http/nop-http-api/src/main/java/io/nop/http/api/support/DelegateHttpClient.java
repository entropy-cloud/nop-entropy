package io.nop.http.api.support;

import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;

import java.util.concurrent.CompletionStage;

public class DelegateHttpClient implements IHttpClient {
    private final IHttpClient httpClient;

    public DelegateHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    protected IHttpClient getHttpClient() {
        return httpClient;
    }

    protected IHttpClient getRawHttpClient() {
        if (httpClient instanceof DelegateHttpClient)
            return ((DelegateHttpClient) httpClient).getRawHttpClient();
        return httpClient;
    }

    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
        onFetchBegin(request, cancelToken);
        return httpClient.fetchAsync(request, cancelToken);
    }

    @Override
    public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                               DownloadOptions options, ICancelToken cancelToken) {
        onDownloadBegin(request, targetFile, options, cancelToken);
        return httpClient.downloadAsync(request, targetFile, options, cancelToken);
    }

    @Override
    public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options, ICancelToken cancelToken) {
        onUploadBegin(request, inputFile, options, cancelToken);
        return httpClient.uploadAsync(request, inputFile, options, cancelToken);
    }

    protected void onFetchBegin(HttpRequest request, ICancelToken cancelToken) {
        onRequestBegin(request, cancelToken);
    }

    protected void onDownloadBegin(HttpRequest request, IHttpOutputFile targetFile, DownloadOptions options, ICancelToken cancelToken) {
        onRequestBegin(request, cancelToken);
    }

    protected void onUploadBegin(HttpRequest request, IHttpInputFile inputFile, UploadOptions options, ICancelToken cancelToken) {
        onRequestBegin(request, cancelToken);
    }

    protected void onRequestBegin(HttpRequest request, ICancelToken cancelToken) {

    }

}