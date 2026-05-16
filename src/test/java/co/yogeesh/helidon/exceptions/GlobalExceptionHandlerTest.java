package co.yogeesh.helidon.exceptions;

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.RoutedPath;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock ServerRequest req;
    @Mock(answer = Answers.RETURNS_SELF) ServerResponse res;
    @Mock HttpPrologue prologue;
    @Mock RoutedPath path;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentException_returns400() {
        handler.handle(req, res, new IllegalArgumentException("bad input"));

        verify(res).status(400);
        assertErrorBody("bad input");
    }

    @Test
    void noSuchElementException_returns404() {
        handler.handle(req, res, new NoSuchElementException("not found"));

        verify(res).status(404);
        assertErrorBody("not found");
    }

    @Test
    void unknownException_returns500_withGenericMessage() {
        when(req.prologue()).thenReturn(prologue);
        when(prologue.method()).thenReturn(Method.GET);
        when(req.path()).thenReturn(path);
        when(path.path()).thenReturn("/profiles");

        handler.handle(req, res, new RuntimeException("db exploded"));

        verify(res).status(500);
        assertErrorBody("Internal server error");
    }

    @Test
    void unknownException_doesNotLeakInternalMessage() {
        when(req.prologue()).thenReturn(prologue);
        when(prologue.method()).thenReturn(Method.GET);
        when(req.path()).thenReturn(path);
        when(path.path()).thenReturn("/profiles");

        handler.handle(req, res, new RuntimeException("secret internal detail"));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        assertThat(((JsonObject) captor.getValue()).getString("error"))
                .doesNotContain("secret internal detail");
    }

    @Test
    void nullMessage_doesNotThrow() {
        handler.handle(req, res, new IllegalArgumentException((String) null));

        verify(res).status(400);
        assertErrorBody("Unknown error");
    }

    private void assertErrorBody(String expectedMessage) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        assertThat(((JsonObject) captor.getValue()).getString("error")).isEqualTo(expectedMessage);
    }
}
