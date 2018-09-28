package com.github.davidmoten.odata.client;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public final class TestingService {

    public static Builder replyWithResource(String path, String resourceName) {
        return new Builder().replyWithResource(path, resourceName);
    }

    public static Builder baseUrl(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return new Builder().baseUrl(url);
    }

    public static Builder pathStyle(PathStyle pathStyle) {
        return new Builder().pathStyle(pathStyle);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static abstract class BuilderBase<T extends BuilderBase<?, R>, R> {
        Map<String, String> content = new HashMap<>();
        String baseUrl = "https://testing.com";
        PathStyle pathStyle = PathStyle.IDENTIFIERS_AS_SEGMENTS;

        @SuppressWarnings("unchecked")
        public T baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T pathStyle(PathStyle pathStyle) {
            this.pathStyle = pathStyle;
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T replyWithResource(String path, String resourceName) {
            content.put(toKey(HttpMethod.GET, baseUrl + path), resourceName);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T expectRequest(String path, String resourceName, HttpMethod method) {
            content.put(toKey(method, baseUrl + path), resourceName);
            return (T) this;
        }

        private static String toKey(HttpMethod method, String url) {
            return method + "_" + url;
        }

        protected Service createService() {
            return new Service() {

                @Override
                public HttpResponse GET(String url, Map<String, String> requestHeaders) {
                    String resourceName = content.get(BuilderBase.toKey(HttpMethod.GET, url));
                    if (resourceName == null) {
                        throw new RuntimeException("GET response not found for url=" + url);
                    }
                    try {
                        URL resource = TestingService.class.getResource(resourceName);
                        if (resource == null) {
                            throw new RuntimeException("resource not found on classpath: " + resourceName);
                        }
                        String text = new String(Files.readAllBytes(Paths.get(resource.toURI())));
                        return new HttpResponse(200, text);
                    } catch (IOException | URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public HttpResponse PATCH(String url, Map<String, String> requestHeaders, String text) {
                    String resourceName = content.get(BuilderBase.toKey(HttpMethod.PATCH, url));
                    if (resourceName == null) {
                        throw new RuntimeException("PATCH response not found for url=" + url);
                    }
                    try {
                        String expected = new String(
                                Files.readAllBytes(Paths.get(TestingService.class.getResource(resourceName).toURI())));

                        JsonNode expectedTree = Serializer.MAPPER.readTree(expected);
                        JsonNode textTree = Serializer.MAPPER.readTree(text);
                        if (expectedTree.equals(textTree)) {
                            return new HttpResponse(HttpURLConnection.HTTP_NO_CONTENT, null);
                        } else {
                            throw new RuntimeException("request does not match expected.\n==== Recieved ====\n" + text
                                    + "\n==== Expected =====\n" + expected);
                        }
                    } catch (IOException | URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public Path getBasePath() {
                    return new Path(baseUrl, pathStyle);
                }
            };
        }

        abstract R build();
    }

    public static abstract class ContainerBuilder<T> extends BuilderBase<ContainerBuilder<T>, T> {

        public abstract T _create(Context context);

        @Override
        public T build() {
            return _create(new Context(Serializer.DEFAULT, createService()));
        }

    }

    public static final class Builder extends BuilderBase<Builder, Service> {

        @Override
        public Service build() {
            return createService();
        }
    }

}
