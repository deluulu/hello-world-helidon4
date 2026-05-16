package co.yogeesh.helidon.filters;

import io.helidon.http.HeaderName;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObservabilityFilterTest {

    @Mock FilterChain chain;
    @Mock RoutingRequest req;
    @Mock RoutingResponse res;
    @Mock ServerRequestHeaders headers;
    @Mock HttpPrologue prologue;
    @Mock RoutedPath path;

    private final ObservabilityFilter filter = new ObservabilityFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private void setupRequest(Map<String, String> requestHeaders) {
        when(req.headers()).thenReturn(headers);
        when(req.prologue()).thenReturn(prologue);
        when(req.path()).thenReturn(path);
        when(prologue.method()).thenReturn(Method.GET);
        when(path.path()).thenReturn("/test");
        when(res.status()).thenReturn(Status.OK_200);
        when(headers.first(any(HeaderName.class))).thenAnswer(inv -> {
            String name = inv.<HeaderName>getArgument(0).lowerCase();
            return Optional.ofNullable(requestHeaders.get(name));
        });
    }

    @Test
    void weight_isOne() {
        assertThat(filter.weight()).isEqualTo(1.0);
    }

    @Test
    void proceedsChain() {
        setupRequest(Map.of());

        filter.filter(chain, req, res);

        verify(chain).proceed();
    }

    @Test
    void populatesMdc_withValuesFromHeaders() {
        setupRequest(Map.of(
                "x-trace-id", "trace-123",
                "x-correlation-id", "corr-456",
                "x-user-id", "user-789"
        ));

        doAnswer(inv -> {
            assertThat(MDC.get("traceId")).isEqualTo("trace-123");
            assertThat(MDC.get("correlationId")).isEqualTo("corr-456");
            assertThat(MDC.get("userId")).isEqualTo("user-789");
            assertThat(MDC.get("spanId")).isNotNull();
            return null;
        }).when(chain).proceed();

        filter.filter(chain, req, res);
    }

    @Test
    void generatesTraceId_whenHeaderAbsent() {
        setupRequest(Map.of());

        doAnswer(inv -> {
            assertThat(MDC.get("traceId")).isNotNull().isNotBlank();
            return null;
        }).when(chain).proceed();

        filter.filter(chain, req, res);
    }

    @Test
    void correlationId_fallsBackToTraceId_whenHeaderAbsent() {
        setupRequest(Map.of("x-trace-id", "trace-abc"));

        doAnswer(inv -> {
            assertThat(MDC.get("correlationId")).isEqualTo(MDC.get("traceId"));
            return null;
        }).when(chain).proceed();

        filter.filter(chain, req, res);
    }

    @Test
    void userId_absentFromMdc_whenHeaderNotProvided() {
        setupRequest(Map.of());

        doAnswer(inv -> {
            assertThat(MDC.get("userId")).isNull();
            return null;
        }).when(chain).proceed();

        filter.filter(chain, req, res);
    }

    @Test
    void clearsMdc_afterSuccessfulRequest() {
        setupRequest(Map.of());

        filter.filter(chain, req, res);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void clearsMdc_evenWhenChainThrows() {
        setupRequest(Map.of());
        doThrow(new RuntimeException("upstream failure")).when(chain).proceed();

        assertThatThrownBy(() -> filter.filter(chain, req, res))
                .isInstanceOf(RuntimeException.class);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
