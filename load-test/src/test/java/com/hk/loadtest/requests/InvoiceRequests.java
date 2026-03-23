package com.hk.loadtest.requests;

import io.gatling.javaapi.core.ChainBuilder;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class InvoiceRequests {

    public static ChainBuilder createInvoice() {
        return exec(
                http("Invoice Service - Create Invoice")
                        .post("http://localhost:9094/api/v1/invoices")
                        .header("Authorization", "Bearer #{authToken}")
                        .body(StringBody("{ \"userId\": #{userId}, \"billType\": \"GSM\", \"institutionName\": \"Hk-fintech\", \"accountNumber\": \"1234567890\", \"amount\": 10.0, \"description\": \"LoadTest Fatura\", \"dueDate\": \"2028-12-31\" }")).asJson()
                        .check(status().in(200, 201))
                        .check(jsonPath("$.id").saveAs("invoiceId"))
        );
    }

    public static ChainBuilder payInvoice() {
        return exec(
                http("Invoice Service - Pay Invoice")
                        .post("http://localhost:9094/api/v1/invoices/#{invoiceId}/pay")
                        .header("Authorization", "Bearer #{authToken}")
                        .body(StringBody("{ \"cardId\": null, \"paymentMethod\": \"WALLET\" }")).asJson()
                        .check(status().in(200, 400))
        );
    }
}
