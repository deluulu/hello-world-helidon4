package co.yogeesh.helidon.service;

import io.helidon.common.mapper.Value;
import io.helidon.common.parameters.Parameters;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbRow;
import io.helidon.http.RoutedPath;
import io.helidon.http.media.ReadableEntity;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class ProfileServiceTracingTest {

    @Mock DbClient db;
    @Mock DbExecute execute;
    @Mock ServerRequest req;
    @Mock(answer = Answers.RETURNS_SELF) ServerResponse res;
    @Mock RoutedPath path;
    @Mock Parameters params;
    @Mock ReadableEntity content;
    @Mock Tracer tracer;
    @Mock Span span;
    @Mock Scope scope;
    @Mock Span.Builder spanBuilder;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(db, tracer);
        lenient().when(db.execute()).thenReturn(execute);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);
        lenient().doReturn(spanBuilder).when(spanBuilder).tag(anyString(), anyString());
        when(spanBuilder.start()).thenReturn(span);
        when(span.activate()).thenReturn(scope);
    }

    // --- span names ---

    @Test
    void getAll_startsSpanWithCorrectName() {
        when(execute.namedQuery("select-all-profiles")).thenReturn(Stream.empty());

        service.getAll(req, res);

        verify(tracer).spanBuilder("profileservice.getAll");
    }

    @Test
    void getById_startsSpanWithCorrectName() {
        stubPathId("id-1");
        DbRow row = stubRow();
        when(execute.namedGet("get-profile-by-id", "id-1")).thenReturn(Optional.of(row));

        service.getById(req, res);

        verify(tracer).spanBuilder("profileservice.getById");
    }

    @Test
    void create_startsSpanWithCorrectName() {
        stubBody(Json.createObjectBuilder().add("username", "bob").build());
        when(execute.namedInsert(anyString(), any(), any(), any())).thenReturn(1L);

        service.create(req, res);

        verify(tracer).spanBuilder("profileservice.create");
    }

    @Test
    void update_startsSpanWithCorrectName() {
        stubPathId("id-1");
        stubBody(Json.createObjectBuilder().build());
        when(execute.namedUpdate(anyString(), any(), any(), any(), any())).thenReturn(1L);

        service.update(req, res);

        verify(tracer).spanBuilder("profileservice.update");
    }

    @Test
    void delete_startsSpanWithCorrectName() {
        stubPathId("id-1");
        when(execute.namedDelete("delete-profile", "id-1")).thenReturn(1L);

        service.delete(req, res);

        verify(tracer).spanBuilder("profileservice.delete");
    }

    // --- builder tags ---

    @Test
    void getById_tagsBuilderWithProfileId() {
        stubPathId("id-42");
        DbRow row = stubRow();
        when(execute.namedGet("get-profile-by-id", "id-42")).thenReturn(Optional.of(row));

        service.getById(req, res);

        verify(spanBuilder).tag("profile.id", "id-42");
    }

    @Test
    void create_tagsBuilderWithUsername() {
        stubBody(Json.createObjectBuilder().add("username", "alice").build());
        when(execute.namedInsert(anyString(), any(), any(), any())).thenReturn(1L);

        service.create(req, res);

        verify(spanBuilder).tag("profile.username", "alice");
    }

    @Test
    void update_tagsBuilderWithProfileId() {
        stubPathId("id-7");
        stubBody(Json.createObjectBuilder().build());
        when(execute.namedUpdate(anyString(), any(), any(), any(), any())).thenReturn(1L);

        service.update(req, res);

        verify(spanBuilder).tag("profile.id", "id-7");
    }

    @Test
    void delete_tagsBuilderWithProfileId() {
        stubPathId("id-9");
        when(execute.namedDelete("delete-profile", "id-9")).thenReturn(1L);

        service.delete(req, res);

        verify(spanBuilder).tag("profile.id", "id-9");
    }

    // --- db.rows tag ---

    @Test
    void create_tagsSpanWithRowCount() {
        stubBody(Json.createObjectBuilder().add("username", "alice").build());
        when(execute.namedInsert(anyString(), any(), any(), any())).thenReturn(2L);

        service.create(req, res);

        verify(span).tag("db.rows", "2");
    }

    @Test
    void update_tagsSpanWithRowCount_whenFound() {
        stubPathId("id-1");
        stubBody(Json.createObjectBuilder().build());
        when(execute.namedUpdate(anyString(), any(), any(), any(), eq("id-1"))).thenReturn(3L);

        service.update(req, res);

        verify(span).tag("db.rows", "3");
    }

    @Test
    void delete_tagsSpanWithRowCount_whenFound() {
        stubPathId("id-1");
        when(execute.namedDelete("delete-profile", "id-1")).thenReturn(1L);

        service.delete(req, res);

        verify(span).tag("db.rows", "1");
    }

    // --- span.end() in finally ---

    @Test
    void getAll_endsSpan_afterSuccess() {
        when(execute.namedQuery("select-all-profiles")).thenReturn(Stream.empty());

        service.getAll(req, res);

        verify(span).end();
    }

    @Test
    void getAll_endsSpan_evenWhenExceptionThrown() {
        when(execute.namedQuery("select-all-profiles")).thenThrow(new RuntimeException("db down"));

        try { service.getAll(req, res); } catch (RuntimeException ignored) {}

        verify(span).end();
    }

    @Test
    void getById_endsSpan_evenWhenExceptionThrown() {
        stubPathId("id-1");
        when(execute.namedGet("get-profile-by-id", "id-1")).thenThrow(new RuntimeException("timeout"));

        try { service.getById(req, res); } catch (RuntimeException ignored) {}

        verify(span).end();
    }

    // --- ERROR status on exception ---

    @Test
    void getAll_setsErrorStatus_onException() {
        RuntimeException ex = new RuntimeException("db failure");
        when(execute.namedQuery("select-all-profiles")).thenThrow(ex);

        try { service.getAll(req, res); } catch (RuntimeException ignored) {}

        verify(span).status(Span.Status.ERROR);
        verify(span).tag("error", "db failure");
    }

    @Test
    void getById_setsErrorStatus_onException() {
        stubPathId("id-1");
        RuntimeException ex = new RuntimeException("timeout");
        when(execute.namedGet("get-profile-by-id", "id-1")).thenThrow(ex);

        try { service.getById(req, res); } catch (RuntimeException ignored) {}

        verify(span).status(Span.Status.ERROR);
        verify(span).tag("error", "timeout");
    }

    @Test
    void create_setsErrorStatus_onException() {
        stubBody(Json.createObjectBuilder().add("username", "bob").build());
        RuntimeException ex = new RuntimeException("constraint violation");
        when(execute.namedInsert(anyString(), any(), any(), any())).thenThrow(ex);

        try { service.create(req, res); } catch (RuntimeException ignored) {}

        verify(span).status(Span.Status.ERROR);
        verify(span).tag("error", "constraint violation");
    }

    // --- ERROR status on 404 (no rows) ---

    @Test
    void update_setsErrorStatus_whenNotFound() {
        stubPathId("ghost-id");
        stubBody(Json.createObjectBuilder().build());
        when(execute.namedUpdate(anyString(), any(), any(), any(), any())).thenReturn(0L);

        service.update(req, res);

        verify(span).status(Span.Status.ERROR);
        verify(span, never()).tag(eq("db.rows"), anyString());
    }

    @Test
    void delete_setsErrorStatus_whenNotFound() {
        stubPathId("ghost-id");
        when(execute.namedDelete("delete-profile", "ghost-id")).thenReturn(0L);

        service.delete(req, res);

        verify(span).status(Span.Status.ERROR);
        verify(span, never()).tag(eq("db.rows"), anyString());
    }

    // --- helpers ---

    private void stubPathId(String id) {
        when(req.path()).thenReturn(path);
        when(path.pathParameters()).thenReturn(params);
        when(params.get("id")).thenReturn(id);
    }

    private void stubBody(JsonObject body) {
        when(req.content()).thenReturn(content);
        when(content.as(JsonObject.class)).thenReturn(body);
    }

    private DbRow stubRow() {
        DbRow r = mock(DbRow.class);
        for (String col : new String[]{"id", "username", "full_name", "avatar_url", "bio", "created_at", "updated_at"}) {
            DbColumn column = mock(DbColumn.class);
            Value<String> v = mock(Value.class);
            when(column.as(String.class)).thenReturn(v);
            when(v.get()).thenReturn("val");
            when(r.column(col)).thenReturn(column);
        }
        return r;
    }
}
