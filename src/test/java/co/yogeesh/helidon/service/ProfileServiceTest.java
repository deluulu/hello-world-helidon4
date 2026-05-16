package co.yogeesh.helidon.service;

import io.helidon.common.mapper.Value;
import io.helidon.common.parameters.Parameters;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbExecute;
import io.helidon.dbclient.DbRow;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.RoutedPath;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock DbClient db;
    @Mock DbExecute execute;
    @Mock ServerRequest req;
    @Mock(answer = Answers.RETURNS_SELF) ServerResponse res;
    @Mock RoutedPath path;
    @Mock Parameters params;
    @Mock ReadableEntity content;
    @Mock DbRow row;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(db);
        when(db.execute()).thenReturn(execute);
    }

    // --- getAll ---

    @Test
    void getAll_sendsJsonArray() {
        stubRowColumns(row, "id-1", "alice", "Alice Smith", null, "Dev", "2024-01-01", "2024-01-02");
        when(execute.namedQuery("select-all-profiles")).thenReturn(Stream.of(row));

        service.getAll(req, res);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        JsonArray array = (JsonArray) captor.getValue();
        assertThat(array).hasSize(1);
        assertThat(array.getJsonObject(0).getString("username")).isEqualTo("alice");
    }

    @Test
    void getAll_sendsEmptyArray_whenNoRows() {
        when(execute.namedQuery("select-all-profiles")).thenReturn(Stream.empty());

        service.getAll(req, res);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        assertThat((JsonArray) captor.getValue()).isEmpty();
    }

    // --- getById ---

    @Test
    void getById_sendsProfileJson_whenFound() {
        stubPathId("id-1");
        stubRowColumns(row, "id-1", "alice", "Alice Smith", null, "Dev", "2024-01-01", "2024-01-02");
        when(execute.namedGet("get-profile-by-id", "id-1")).thenReturn(Optional.of(row));

        service.getById(req, res);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        JsonObject profile = (JsonObject) captor.getValue();
        assertThat(profile.getString("id")).isEqualTo("id-1");
        assertThat(profile.getString("username")).isEqualTo("alice");
    }

    @Test
    void getById_sends404_whenNotFound() {
        stubPathId("missing-id");
        when(execute.namedGet("get-profile-by-id", "missing-id")).thenReturn(Optional.empty());

        service.getById(req, res);

        verify(res).status(404);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        assertThat(((JsonObject) captor.getValue()).getString("error"))
                .contains("missing-id");
    }

    // --- create ---

    @Test
    void create_sends201WithCreatedCount() {
        JsonObject body = Json.createObjectBuilder()
                .add("username", "bob")
                .add("full_name", "Bob Jones")
                .add("bio", "Engineer")
                .build();
        when(req.content()).thenReturn(content);
        when(content.as(JsonObject.class)).thenReturn(body);
        when(execute.namedInsert(eq("insert-profile"), any(), any(), any())).thenReturn(1L);

        service.create(req, res);

        verify(res).status(201);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        assertThat(((JsonObject) captor.getValue()).getJsonNumber("created").longValue()).isEqualTo(1L);
    }

    // --- update ---

    @Test
    void update_sendsUpdatedCount_whenFound() {
        stubPathId("id-1");
        JsonObject body = Json.createObjectBuilder()
                .add("full_name", "Alice Updated")
                .add("bio", "Senior Dev")
                .add("avatar_url", "https://example.com/pic.png")
                .build();
        when(req.content()).thenReturn(content);
        when(content.as(JsonObject.class)).thenReturn(body);
        when(execute.namedUpdate(eq("update-profile"), any(), any(), any(), eq("id-1"))).thenReturn(1L);

        service.update(req, res);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        assertThat(((JsonObject) captor.getValue()).getJsonNumber("updated").longValue()).isEqualTo(1L);
    }

    @Test
    void update_sends404_whenNotFound() {
        stubPathId("ghost-id");
        JsonObject body = Json.createObjectBuilder().build();
        when(req.content()).thenReturn(content);
        when(content.as(JsonObject.class)).thenReturn(body);
        when(execute.namedUpdate(eq("update-profile"), any(), any(), any(), eq("ghost-id"))).thenReturn(0L);

        service.update(req, res);

        verify(res).status(404);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        assertThat(((JsonObject) captor.getValue()).getString("error")).contains("ghost-id");
    }

    // --- delete ---

    @Test
    void delete_sendsDeletedCount_whenFound() {
        stubPathId("id-1");
        when(execute.namedDelete("delete-profile", "id-1")).thenReturn(1L);

        service.delete(req, res);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        assertThat(((JsonObject) captor.getValue()).getJsonNumber("deleted").longValue()).isEqualTo(1L);
    }

    @Test
    void delete_sends404_whenNotFound() {
        stubPathId("ghost-id");
        when(execute.namedDelete("delete-profile", "ghost-id")).thenReturn(0L);

        service.delete(req, res);

        verify(res).status(404);
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        assertThat(((JsonObject) captor.getValue()).getString("error")).contains("ghost-id");
    }

    // --- helpers ---

    private void stubPathId(String id) {
        when(req.path()).thenReturn(path);
        when(path.pathParameters()).thenReturn(params);
        when(params.get("id")).thenReturn(id);
    }

    @SuppressWarnings("unchecked")
    private DbColumn col(String value) {
        DbColumn column = mock(DbColumn.class);
        Value<String> v = mock(Value.class);
        when(column.as(String.class)).thenReturn(v);
        when(v.get()).thenReturn(value);
        return column;
    }

    private void stubRowColumns(DbRow r, String id, String username, String fullName,
                                String avatarUrl, String bio, String createdAt, String updatedAt) {
        // Pre-compute columns before any when() chain to avoid corrupting Mockito's pending-stub state
        DbColumn idCol       = col(id);
        DbColumn usernameCol = col(username);
        DbColumn fullNameCol = col(fullName);
        DbColumn avatarCol   = col(avatarUrl);
        DbColumn bioCol      = col(bio);
        DbColumn createdCol  = col(createdAt);
        DbColumn updatedCol  = col(updatedAt);

        when(r.column("id")).thenReturn(idCol);
        when(r.column("username")).thenReturn(usernameCol);
        when(r.column("full_name")).thenReturn(fullNameCol);
        when(r.column("avatar_url")).thenReturn(avatarCol);
        when(r.column("bio")).thenReturn(bioCol);
        when(r.column("created_at")).thenReturn(createdCol);
        when(r.column("updated_at")).thenReturn(updatedCol);
    }
}
