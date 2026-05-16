package co.yogeesh.helidon;

import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicationTest {

    @Mock ServerRequest req;
    @Mock(answer = Answers.RETURNS_SELF) ServerResponse res;

    @Test
    void healthHandler_returnsStatusUp() {
        Application.healthHandler(req, res);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(res).send(captor.capture());
        JsonObject body = (JsonObject) captor.getValue();
        assertThat(body.getString("status")).isEqualTo("UP");
    }
}
