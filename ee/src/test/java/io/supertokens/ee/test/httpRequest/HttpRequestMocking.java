package io.supertokens.ee.test.httpRequest;

import io.supertokens.Main;
import io.supertokens.ResourceDistributor;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class HttpRequestMocking extends ResourceDistributor.SingletonResource {
    private static final String RESOURCE_KEY = "io.supertokens.test.httpRequest.HttpRequestMocking";

    private Map<String, URLGetter> urlMap = new HashMap<>();

    private HttpRequestMocking() {
    }

    public static HttpRequestMocking getInstance(Main main) {
        ResourceDistributor.SingletonResource instance = main.getResourceDistributor().getResource(RESOURCE_KEY);
        if (instance == null) {
            instance = main.getResourceDistributor().setResource(RESOURCE_KEY, new HttpRequestMocking());
        }
        return (HttpRequestMocking) instance;
    }

    public void setMockURL(String key, URLGetter urlGetter) {
        urlMap.put(key, urlGetter);
    }

    public URL getMockURL(String key, String url) throws MalformedURLException {
        URLGetter urlGetter = urlMap.get(key);
        if (urlGetter != null) {
            return urlGetter.getUrl(url);
        }
        return null;
    }

    public abstract static class URLGetter {
        public abstract URL getUrl(String url) throws MalformedURLException;
    }
}
