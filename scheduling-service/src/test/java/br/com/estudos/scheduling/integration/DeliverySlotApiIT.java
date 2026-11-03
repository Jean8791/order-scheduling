package br.com.estudos.scheduling.integration;

import br.com.estudos.scheduling.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeliverySlotApiIT extends IntegrationTest {

    @Test
    void createsSlotRejectsDuplicateAndListsAvailability() {
        LocalDate date = uniqueDate();
        Map<String, Object> slot = Map.of("state", "SP", "date", date.toString(), "period", "AFTERNOON", "capacity", 4);

        ResponseEntity<JsonNode> created = call(HttpMethod.POST, "/delivery-slots", slot);
        ResponseEntity<JsonNode> duplicated = call(HttpMethod.POST, "/delivery-slots", slot);
        ResponseEntity<JsonNode> list = call(HttpMethod.GET, "/delivery-slots?state=SP&from=" + date + "&to=" + date, null);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().path("available").asInt()).isEqualTo(4);
        assertThat(duplicated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicated.getBody().path("code").asText()).isEqualTo("CONFLICT");
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(1);
        assertThat(list.getBody().get(0).path("id").asText()).isEqualTo(created.getBody().path("id").asText());
    }

    @Test
    void requestWithoutTokenReturns401() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/delivery-slots?from=2100-01-01&to=2100-01-02", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().path("message").asText()).isEqualTo("Token ausente, inválido ou expirado");
    }

    @Test
    void tokenFromAnotherIssuerReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFromAnotherIssuer());

        ResponseEntity<JsonNode> response = rest.exchange("/delivery-slots?from=2100-01-01&to=2100-01-02", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthIsPublic() {
        assertThat(rest.getForEntity("/actuator/health", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
