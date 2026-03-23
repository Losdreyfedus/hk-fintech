package com.hk.loadtest.requests;

import io.gatling.javaapi.core.ChainBuilder;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class IdentityRequests {

    public static ChainBuilder login() {
        return exec(
                http("Identity Service - Login")
                        .post("/api/v1/auth/login")
                        .body(StringBody("{ \"email\": \"loadtest@hk.com\", \"password\": \"P@ssword123\" }")).asJson()
                        .check(status().is(200))
                        .check(jsonPath("$.token").saveAs("authToken"))
        );
    }

    public static ChainBuilder validate() {
        return exec(
                http("Identity Service - Validate Token")
                        .get("/api/v1/auth/validate")
                        .queryParam("token", "#{authToken}")
                        .check(status().is(200))
                        .check(bodyString().saveAs("userId"))
        );
    }
}
