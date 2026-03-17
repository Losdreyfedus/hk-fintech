package com.hk.systemtest;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.awaitility.Awaitility.await;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullSystemE2ETest {

    private static final int IDENTITY_PORT = 9090;
    private static final int WALLET_PORT = 9091;
    private static final int CARD_PORT = 9092;
    private static final int PAYMENT_PORT = 9093;
    private static final int INVOICE_PORT = 9094;

    @Container
    public static DockerComposeContainer<?> environment =
            new DockerComposeContainer<>(new File("../docker-compose-e2e.yml"))
                    .withExposedService("identity-service", IDENTITY_PORT, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                    .withExposedService("card-service", CARD_PORT, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                    .withExposedService("wallet-service", WALLET_PORT, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                    .withExposedService("payment-service", PAYMENT_PORT, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)))
                    .withExposedService("invoice-service", INVOICE_PORT, Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));

    private static String jwtToken;
    private static Number cardId;
    private static String cardToken;
    private static Long userId;
    private static Number invoiceId;

    @Test
    @Order(1)
    @DisplayName("1. User Registration & Login")
    void shouldRegisterAndLogin() {
        Map<String, Object> registerRequest = new HashMap<>();
        registerRequest.put("firstName", "E2E");
        registerRequest.put("lastName", "User");
        registerRequest.put("email", "e2e@hk-fintech.com");
        registerRequest.put("password", "P@ssword123");
        registerRequest.put("phoneNumber", "5550001122");

        given()
                .port(getMappedPort("identity-service", IDENTITY_PORT))
                .contentType(ContentType.JSON)
                .body(registerRequest)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .log().ifError()
                .statusCode(200);

        Map<String, Object> loginRequest = new HashMap<>();
        loginRequest.put("email", "e2e@hk-fintech.com");
        loginRequest.put("password", "P@ssword123");

        jwtToken = given()
                .port(getMappedPort("identity-service", IDENTITY_PORT))
                .contentType(ContentType.JSON)
                .body(loginRequest)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .log().ifError()
                .statusCode(200)
                .extract()
                .path("token");

        assertNotNull(jwtToken);
        System.out.println("DEBUG: JWT Token obtained.");

        userId = given()
                .port(getMappedPort("identity-service", IDENTITY_PORT))
                .queryParam("token", jwtToken)
                .when()
                .get("/api/v1/auth/validate")
                .then()
                .log().ifError()
                .statusCode(200)
                .extract()
                .as(Long.class);

        assertNotNull(userId);
        System.out.println("DEBUG: User ID obtained: " + userId);

        System.out.println("DEBUG: Waiting for wallet creation (up to 60s)...");
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        Response response = given()
                                .port(getMappedPort("wallet-service", WALLET_PORT))
                                .header("Authorization", "Bearer " + jwtToken)
                                .when()
                                .get("/api/v1/wallets/user/" + userId);
                        
                        return response.getStatusCode() == 200;
                    } catch (Exception e) {
                        return false;
                    }
                });
        System.out.println("DEBUG: Wallet confirmed.");
    }

    @Test
    @Order(2)
    @DisplayName("2. Add Card")
    void shouldAddCard() {
        assumeTrue(jwtToken != null, "JWT Token must be present");

        Map<String, Object> cardRequest = new HashMap<>();
        cardRequest.put("cardNumber", "4242424242424242");
        cardRequest.put("expireMonth", "12");
        cardRequest.put("expireYear", "2028");
        cardRequest.put("cvv", "123");
        cardRequest.put("cardHolder", "E2E User");
        cardRequest.put("cardAlias", "My Test Card");

        var response = given()
                .port(getMappedPort("card-service", CARD_PORT))
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .body(cardRequest)
                .when()
                .post("/api/v1/cards")
                .then()
                .log().ifError()
                .statusCode(201)
                .extract();

        cardId = response.path("id");
        cardToken = response.path("cardToken");

        assertNotNull(cardId);
        assertNotNull(cardToken);
        System.out.println("DEBUG: Card added. ID: " + cardId);
    }

    @Test
    @Order(3)
    @DisplayName("3. Top-up Wallet")
    void shouldTopupWallet() {
        assumeTrue(jwtToken != null, "JWT Token must be present");
        assumeTrue(cardId != null, "Card ID must be present");

        Map<String, Object> topupRequest = new HashMap<>();
        topupRequest.put("cardId", cardId.longValue());
        topupRequest.put("amount", 1000.0);

        given()
                .port(getMappedPort("wallet-service", WALLET_PORT))
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .body(topupRequest)
                .when()
                .post("/api/v1/wallets/top-up")
                .then()
                .log().ifError()
                .statusCode(202); 
        
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            given()
                    .port(getMappedPort("wallet-service", WALLET_PORT))
                    .header("Authorization", "Bearer " + jwtToken)
                    .when()
                    .get("/api/v1/wallets/user/" + userId)
                    .then()
                    .statusCode(200)
                    .body("balance", equalTo(1000.0f));
        });
        System.out.println("DEBUG: Top-up confirmed.");
    }

    @Test
    @Order(4)
    @DisplayName("4. Create and Pay Invoice")
    void shouldCreateAndPayInvoice() {
        assumeTrue(jwtToken != null, "JWT Token must be present");
        assumeTrue(userId != null, "User ID must be present");

        Map<String, Object> invoiceRequest = new HashMap<>();
        invoiceRequest.put("userId", userId);
        invoiceRequest.put("billType", "GSM");
        invoiceRequest.put("institutionName", "Hk-fintech");
        invoiceRequest.put("accountNumber", "5550001122");
        invoiceRequest.put("amount", 250.0);
        invoiceRequest.put("description", "E2E Test Invoice");
        invoiceRequest.put("dueDate", "2028-12-31"); // MUST BE FUTURE

        invoiceId = given()
                .port(getMappedPort("invoice-service", INVOICE_PORT))
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .body(invoiceRequest)
                .when()
                .post("/api/v1/invoices")
                .then()
                .log().ifError()
                .statusCode(201)
                .extract()
                .path("id");

        assertNotNull(invoiceId);
        System.out.println("DEBUG: Invoice created. ID: " + invoiceId);

        System.out.println("DEBUG: Paying invoice with wallet...");
        Map<String, Object> payRequest = new HashMap<>();
        payRequest.put("cardId", null);
        payRequest.put("paymentMethod", "WALLET");

        given()
                .port(getMappedPort("invoice-service", INVOICE_PORT))
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .body(payRequest)
                .when()
                .post("/api/v1/invoices/" + invoiceId.longValue() + "/pay")
                .then()
                .log().all()
                .statusCode(200);

        System.out.println("DEBUG: Invoice paid. Verifying balance reduction...");

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            given()
                    .port(getMappedPort("wallet-service", WALLET_PORT))
                    .header("Authorization", "Bearer " + jwtToken)
                    .when()
                    .get("/api/v1/wallets/user/" + userId)
                    .then()
                    .statusCode(200)
                    .body("balance", equalTo(750.0f));
        });
        System.out.println("DEBUG: Invoice payment and balance reduction confirmed.");
    }

    private int getMappedPort(String serviceName, int originalPort) {
        return environment.getServicePort(serviceName, originalPort);
    }
}
