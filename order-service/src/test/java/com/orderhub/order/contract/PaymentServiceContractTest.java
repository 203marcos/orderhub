package com.orderhub.order.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.orderhub.order.client.PaymentClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer contract for order-service calling payment-service's
 * GET /api/v1/payments/order/{orderId}. The production caller is {@link PaymentClient};
 * this test drives the same endpoint and deserializes into the same DTO the client uses,
 * so the contract stays honest with real code.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-service", pactVersion = PactSpecVersion.V3)
class PaymentServiceContractTest {

    private static final String ORDER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String USER_ID = "9f8b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d";

    @Pact(consumer = "order-service", provider = "payment-service")
    public RequestResponsePact getPaymentByOrderId(PactDslWithProvider builder) {
        return builder
                .given("payment exists for order")
                .uponReceiving("get payment by order id")
                .path("/api/v1/payments/order/" + ORDER_ID)
                .method("GET")
                // payment-service enforces ownership on this endpoint, so the caller's
                // identity is part of the contract, not an optional extra.
                .headers(Map.of("X-User-Id", USER_ID))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .uuid("id")
                        .uuid("orderId", ORDER_ID)
                        .uuid("userId", USER_ID)
                        .decimalType("amount", 99.99)
                        .stringType("status", "APPROVED")
                )
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getPaymentByOrderId")
    void shouldGetPaymentForOrder(MockServer mockServer) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", USER_ID);

        ResponseEntity<PaymentClient.PaymentInfo> response = restTemplate.exchange(
                mockServer.getUrl() + "/api/v1/payments/order/" + ORDER_ID,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PaymentClient.PaymentInfo.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PaymentClient.PaymentInfo payment = response.getBody();
        assertThat(payment).isNotNull();
        assertThat(payment.orderId()).isEqualTo(UUID.fromString(ORDER_ID));
        assertThat(payment.status()).isEqualTo("APPROVED");
        assertThat(payment.amount()).isEqualByComparingTo("99.99");
    }
}
