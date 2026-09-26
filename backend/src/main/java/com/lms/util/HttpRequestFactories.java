package com.lms.util;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Request factories for calls to the internal Python services. */
public final class HttpRequestFactories {

    private HttpRequestFactories() {
    }

    /**
     * JDK client pinned to HTTP/1.1. The JDK defaults to HTTP/2 and sends an h2c upgrade request on plain
     * http, which uvicorn rejects (the request body is lost and FastAPI answers 422).
     */
    public static JdkClientHttpRequestFactory http11(long timeoutMs) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 5000)))
                .build());
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return factory;
    }
}
