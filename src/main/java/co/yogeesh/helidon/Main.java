package co.yogeesh.helidon;

import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;

import jakarta.json.Json;

public class Main {

    public static void main(String[] args) {
        LogConfig.configureRuntime();

        Config config = Config.create();
        Config.global(config);

        DbClient dbClient = DbClient.create(config.get("db"));

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(routing -> routing
                        .get("/health", (req, res) ->
                                res.send(Json.createObjectBuilder()
                                        .add("status", "UP")
                                        .build()))
                        .register("/profiles", new ProfileService(dbClient)))
                .build()
                .start();

        System.out.printf("Server started: http://localhost:%d%n", server.port());
        System.out.println("Endpoints:");
        System.out.println("  GET    /health");
        System.out.println("  GET    /profiles");
        System.out.println("  GET    /profiles/{id}");
        System.out.println("  POST   /profiles");
        System.out.println("  PUT    /profiles/{id}");
        System.out.println("  DELETE /profiles/{id}");
    }
}
