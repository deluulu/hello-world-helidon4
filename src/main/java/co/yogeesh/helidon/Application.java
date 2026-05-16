package co.yogeesh.helidon;

import co.yogeesh.helidon.exceptions.GlobalExceptionHandler;
import co.yogeesh.helidon.filters.ObservabilityFilter;
import co.yogeesh.helidon.service.ProfileService;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    static void healthHandler(ServerRequest req, ServerResponse res) {
        res.send(Json.createObjectBuilder().add("status", "UP").build());
    }

    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Config config = Config.create();
        Config.global(config);

        DbClient dbClient = DbClient.create(config.get("db"));

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(routing -> routing
                        .addFilter(new ObservabilityFilter())
                        .error(Exception.class, new GlobalExceptionHandler())
                        .get("/health", Application::healthHandler)
                        .register("/profiles", new ProfileService(dbClient)))
                .build()
                .start();

        log.info("Server started: http://localhost:{}", server.port());

        log.info("Endpoints: GET /health, GET /profiles, GET /profiles/{id}, POST /profiles, PUT /profiles/{id}, DELETE /profiles/{id}");
    }
}
