package co.yogeesh.helidon.service;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * CRUD service for the `profiles` table in Supabase (hello-world-v2).
 *
 * Note: profiles.id is a FK to auth.users.id. For POST, you must supply
 * a UUID that already exists in Supabase Auth, or remove the FK constraint
 * during development.
 */
public class ProfileService implements HttpService {

    private final DbClient db;

    public ProfileService(DbClient db) {
        this.db = db;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::getAll)
             .get("/{id}", this::getById)
             .post("/", this::create)
             .put("/{id}", this::update)
             .delete("/{id}", this::delete);
    }

    // GET /profiles
    void getAll(ServerRequest req, ServerResponse res) {
        JsonArrayBuilder array = Json.createArrayBuilder();
        db.execute()
          .namedQuery("select-all-profiles")
          .forEach(row -> array.add(toJson(row)));
        res.send(array.build());
    }

    // GET /profiles/{id}
    void getById(ServerRequest req, ServerResponse res) {
        String id = req.path().pathParameters().get("id");
        db.execute()
          .namedGet("get-profile-by-id", id)
          .ifPresentOrElse(
                  row -> res.send(toJson(row)),
                  () -> res.status(404).send(errorJson("Profile not found: " + id)));
    }

    // POST /profiles   body: { "id": "<auth-user-uuid>", "username": "...", "full_name": "...", "bio": "..." }
    void create(ServerRequest req, ServerResponse res) {
        JsonObject body = req.content().as(JsonObject.class);
        String username = body.getString("username", null);
        String fullName = body.getString("full_name", null);
        String bio      = body.getString("bio", null);

        long count = db.execute()
                       .namedInsert("insert-profile", username, fullName, bio);

        res.status(201).send(Json.createObjectBuilder()
                .add("created", count)
                .build());
    }

    // PUT /profiles/{id}   body: { "full_name": "...", "bio": "...", "avatar_url": "..." }
    void update(ServerRequest req, ServerResponse res) {
        String id      = req.path().pathParameters().get("id");
        JsonObject body = req.content().as(JsonObject.class);

        long count = db.execute()
                       .namedUpdate("update-profile",
                               body.getString("full_name", null),
                               body.getString("bio", null),
                               body.getString("avatar_url", null),
                               id);

        if (count > 0) {
            res.send(Json.createObjectBuilder().add("updated", count).build());
        } else {
            res.status(404).send(errorJson("Profile not found: " + id));
        }
    }

    // DELETE /profiles/{id}
    void delete(ServerRequest req, ServerResponse res) {
        String id    = req.path().pathParameters().get("id");
        long count   = db.execute().namedDelete("delete-profile", id);

        if (count > 0) {
            res.send(Json.createObjectBuilder().add("deleted", count).build());
        } else {
            res.status(404).send(errorJson("Profile not found: " + id));
        }
    }

    private JsonObject toJson(DbRow row) {
        JsonObjectBuilder b = Json.createObjectBuilder();
        addStr(b, row, "id");
        addStr(b, row, "username");
        addStr(b, row, "full_name");
        addStr(b, row, "avatar_url");
        addStr(b, row, "bio");
        addStr(b, row, "created_at");
        addStr(b, row, "updated_at");
        return b.build();
    }

    private void addStr(JsonObjectBuilder b, DbRow row, String col) {
        String val = row.column(col).as(String.class).get();
        if (val != null) {
            b.add(col, val);
        } else {
            b.addNull(col);
        }
    }

    private JsonObject errorJson(String message) {
        return Json.createObjectBuilder().add("error", message).build();
    }
}
