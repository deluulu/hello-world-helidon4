package co.yogeesh.helidon.exceptions;

import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;

public class GlobalExceptionHandler implements ErrorHandler<Exception> {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    public void handle(ServerRequest req, ServerResponse res, Exception ex) {
        if (ex instanceof IllegalArgumentException) {
            log.warn("Bad request: {}", ex.getMessage());
            sendError(res, 400, ex.getMessage());
        } else if (ex instanceof NoSuchElementException) {
            log.warn("Not found: {}", ex.getMessage());
            sendError(res, 404, ex.getMessage());
        } else {
            log.error("Unhandled exception for {} {}", req.prologue().method().text(),
                    req.path().path(), ex);
            sendError(res, 500, "Internal server error");
        }
    }

    private void sendError(ServerResponse res, int status, String message) {
        JsonObject body = Json.createObjectBuilder()
                .add("error", message != null ? message : "Unknown error")
                .build();
        res.status(status).send(body);
    }
}
