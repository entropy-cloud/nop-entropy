package io.nop.plugin.manager.resolver;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.beans.ArtifactCoordinates;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.URLHelper;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.support.DefaultHttpOutputFile;
import io.nop.plugin.manager.PluginManagerConstants;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.List;

import static io.nop.plugin.manager.PluginManagerErrors.ARG_FILE_NAME;
import static io.nop.plugin.manager.PluginManagerErrors.ARG_PARAM_NAME;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_DOWNLOAD_RENAME_FILE_FAIL;
import static io.nop.plugin.manager.PluginManagerErrors.ERR_PLUGIN_INVALID_PARAM_NAME;

/**
 * 通过REST请求下载文件到本地缓存目录。下载文件的完整性有SHA256校验码保证。
 */
public class HttpPluginResourceResolver implements IPluginResourceResolver {
    static final Logger LOG = LoggerFactory.getLogger(HttpPluginResourceResolver.class);

    private File cacheDir;
    private IHttpClient httpClient;
    private String pluginServiceUrl;

    @InjectValue("@cfg:nop.plugin.cache-dir|/nop/plugin")
    public void setCacheDir(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    @InjectValue("@cfg:nop.plugin.service-url")
    public void setPluginServiceUrl(String pluginServiceUrl) {
        this.pluginServiceUrl = pluginServiceUrl;
    }

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<URL> resolvePluginResource(ArtifactCoordinates coordinates) {
        String jarFilePath = coordinates.getJarFilePath();
        File jarFile = new File(cacheDir, jarFilePath);
        if (jarFile.exists())
            return List.of(URLHelper.toURL(jarFile));

        download(coordinates, jarFile);
        return List.of(URLHelper.toURL(jarFile));
    }

    void download(ArtifactCoordinates coordinates, File jarFile) {
        File tmp = null;
        try {
            tmp = File.createTempFile(jarFile.getName(), ".tmp", jarFile.getParentFile());
            String url = buildServiceUrl(coordinates);

            HttpRequest request = HttpRequest.get(url);

            httpClient.download(request, new DefaultHttpOutputFile(tmp), null, null);

            boolean bRet = FileHelper.moveFile(tmp, jarFile);
            //tmp.delete();

            if (!bRet)
                throw new NopException(ERR_PLUGIN_DOWNLOAD_RENAME_FILE_FAIL).param(ARG_FILE_NAME, tmp.getName());
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            try {
                FileHelper.deleteIfExists(tmp);
            } catch (Exception e) {
                LOG.error("nop.plugin.delete-file-fail", e);
            }
        }

    }

    private String buildServiceUrl(ArtifactCoordinates coordinates) {
        return StringHelper.renderTemplate(pluginServiceUrl, name -> {
            if (name.equals(PluginManagerConstants.VAR_PLUGIN_GROUP_ID)) {
                return StringHelper.encodeUriPath(coordinates.getGroupId());
            } else if (name.equals(PluginManagerConstants.VAR_PLUGIN_ARTIFACT_ID)) {
                return StringHelper.encodeUriPath(coordinates.getArtifactId());
            } else if (name.equals(PluginManagerConstants.VAR_PLUGIN_VERSION)) {
                return StringHelper.encodeUriPath(coordinates.getVersion());
            } else {
                throw new NopException(ERR_PLUGIN_INVALID_PARAM_NAME)
                        .param(ARG_PARAM_NAME, name);
            }
        });
    }
}