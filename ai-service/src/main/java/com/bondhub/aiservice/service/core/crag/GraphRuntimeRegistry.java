package com.bondhub.aiservice.service.core.crag;

import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GraphRuntimeRegistry {

    private static final Map<String, Sinks.Many<String>> SINKS = new ConcurrentHashMap<>();

    private GraphRuntimeRegistry() {
    }

    public static void registerSink(String convId, Sinks.Many<String> sink) {
        SINKS.put(convId, sink);
    }

    public static Sinks.Many<String> getSink(String convId) {
        return SINKS.get(convId);
    }

    public static void unregisterSink(String convId) {
        SINKS.remove(convId);
    }
}
