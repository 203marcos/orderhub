package com.orderhub.order.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-service")
class PaymentServiceContractTest {

    @Pact(consumer = "order-service", provider = "payment-service")
    RequestResponsePact getPaymentByOrderId(PactDslWithProvider builder) {
        return builder
                .given("payment exists for order")
                .uponReceiving("get payment by order id")
                .path("/api/v1/payments/order/550e8400-e29b-41d4-a716-446655440000")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .uuid("id")
                        .uuid("orderId", "550e8400-e29b-41d4-a716-446655440000")
                        .uuid("userId")
                        .decimalType("amount", 99.99)
                        .stringType("status", "APPROVED")
                )
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getPaymentByOrderId")
    void shouldGetPaymentForOrder(MockServer mockServer) {
        RestTemplate restTemplate = new RestTemplate();
        var response = restTemplate.getForEntity(
                mockServer.getUrl() + "/api/v1/payments/order/550e8400-e29b-41d4-a716-446655440000",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("orderId");
        assertThat(response.getBody()).containsKey("status");
    }
}
