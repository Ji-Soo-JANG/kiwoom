package com.example.kiwoom.config;

import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter implements WebFilter {
    public static final String TRACE_ID_ATTRIBUTE = RequestTraceFilter.class.getName() + ".traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = traceId(exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER));
        long startedAt = System.nanoTime();
        exchange.getAttributes().put(TRACE_ID_ATTRIBUTE, traceId);
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        return chain.filter(exchange).doFinally(signal -> {
            MDC.put("traceId", traceId);
            try {
                log.info("http_request_completed {} {} {} {}",
                        StructuredArguments.keyValue("method", exchange.getRequest().getMethod().name()),
                        StructuredArguments.keyValue("path", exchange.getRequest().getPath().value()),
                        StructuredArguments.keyValue("status", exchange.getResponse().getStatusCode() == null
                                ? 200 : exchange.getResponse().getStatusCode().value()),
                        StructuredArguments.keyValue("durationMs", (System.nanoTime() - startedAt) / 1_000_000));
            } finally {
                MDC.remove("traceId");
            }
        });
    }

    private String traceId(String candidate) {
        return candidate != null && candidate.matches("[A-Za-z0-9_-]{8,64}")
                ? candidate : UUID.randomUUID().toString();
    }
}
