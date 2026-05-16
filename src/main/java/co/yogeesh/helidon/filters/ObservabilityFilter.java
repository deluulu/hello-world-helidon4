package co.yogeesh.helidon.filters;

import io.helidon.common.Weighted;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

public class ObservabilityFilter implements Filter, Weighted {

    @Override
    public double weight() {
        return 1;
    }

    private static final Logger log = LoggerFactory.getLogger(ObservabilityFilter.class);

    @Override
    public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
        long start = System.currentTimeMillis();

        String traceId = req.headers().first(HeaderNames.create("X-Trace-Id"))
                .orElseGet(() -> UUID.randomUUID().toString());
        String spanId = UUID.randomUUID().toString();
        String correlationId = req.headers().first(HeaderNames.create("X-Correlation-Id"))
                .orElse(traceId);
        String userId = req.headers().first(HeaderNames.create("X-User-Id")).orElse(null);

        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);
        MDC.put("correlationId", correlationId);
        if (userId != null) {
            MDC.put("userId", userId);
        }

        String method = req.prologue().method().text();
        String path = req.path().path();

        log.info("{} {}", method, path);

        try {
            chain.proceed();
        } finally {
            log.info("{} {} -> {} ({}ms)", method, path, res.status().code(), System.currentTimeMillis() - start);
            MDC.clear();
        }
    }
}
