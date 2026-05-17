package co.yogeesh.helidon.service;

import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.DbRow;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileService implements HttpService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final DbClient db;
    private final Tracer   tracer;

    public ProfileService(DbClient db) {
        this(db, Tracer.global());
    }

    ProfileService(DbClient db, Tracer tracer) {
        this.db     = db;
        this.tracer = tracer;
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
        Span span = tracer.spanBuilder("profileservice.getAll").start();
        try (Scope scope = span.activate()) {
            JsonArrayBuilder array = Json.createArrayBuilder();
            db.execute()
              .namedQuery("select-all-profiles")
              .forEach(row -> array.add(toJson(row)));
            res.send(array.build());
        } catch (Exception e) {
            span.status(Span.Status.ERROR);
            span.tag("error", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // GET /profiles/{id}
    void getById(ServerRequest req, ServerResponse res) {
        String id = req.path().pathParameters().get("id");
        Span span = tracer.spanBuilder("profileservice.getById")
                .tag("profile.id", id)
                .start();
        try (Scope scope = span.activate()) {
            db.execute()
              .namedGet("get-profile-by-id", id)
              .ifPresentOrElse(
                      row -> res.send(toJson(row)),
                      () -> res.status(404).send(errorJson("Profile not found: " + id)));
        } catch (Exception e) {
            span.status(Span.Status.ERROR);
            span.tag("error", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // POST /profiles   body: { "username": "...", "full_name": "...", "bio": "..." }
    void create(ServerRequest req, ServerResponse res) {
        JsonObject body = req.content().as(JsonObject.class);
        String username = body.getString("username", null);
        String fullName = body.getString("full_name", null);
        String bio      = body.getString("bio", null);

        Span span = tracer.spanBuilder("profileservice.create")
                .tag("profile.username", username)
                .start();
        try (Scope scope = span.activate()) {
            long count = db.execute()
                           .namedInsert("insert-profile", username, fullName, bio);
            span.tag("db.rows", String.valueOf(count));
            res.status(201).send(Json.createObjectBuilder()
                    .add("created", count)
                    .build());
        } catch (Exception e) {
            span.status(Span.Status.ERROR);
            span.tag("error", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // PUT /profiles/{id}   body: { "full_name": "...", "bio": "...", "avatar_url": "..." }
    void update(ServerRequest req, ServerResponse res) {
        String id       = req.path().pathParameters().get("id");
        JsonObject body = req.content().as(JsonObject.class);

        Span span = tracer.spanBuilder("profileservice.update")
                .tag("profile.id", id)
                .start();
        try (Scope scope = span.activate()) {
            long count = db.execute()
                           .namedUpdate("update-profile",
                                   body.getString("full_name", null),
                                   body.getString("bio", null),
                                   body.getString("avatar_url", null),
                                   id);
            if (count > 0) {
                span.tag("db.rows", String.valueOf(count));
                res.send(Json.createObjectBuilder().add("updated", count).build());
            } else {
                span.status(Span.Status.ERROR);
                res.status(404).send(errorJson("Profile not found: " + id));
            }
        } catch (Exception e) {
            span.status(Span.Status.ERROR);
            span.tag("error", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    // DELETE /profiles/{id}
    void delete(ServerRequest req, ServerResponse res) {
        String id = req.path().pathParameters().get("id");
        Span span = tracer.spanBuilder("profileservice.delete")
                .tag("profile.id", id)
                .start();
        try (Scope scope = span.activate()) {
            long count = db.execute().namedDelete("delete-profile", id);
            if (count > 0) {
                span.tag("db.rows", String.valueOf(count));
                res.send(Json.createObjectBuilder().add("deleted", count).build());
            } else {
                span.status(Span.Status.ERROR);
                res.status(404).send(errorJson("Profile not found: " + id));
            }
        } catch (Exception e) {
            span.status(Span.Status.ERROR);
            span.tag("error", e.getMessage());
            throw e;
        } finally {
            span.end();
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
