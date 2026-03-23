package com.hk.loadtest.requests;

import io.gatling.javaapi.core.ChainBuilder;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class WalletRequests {

    public static ChainBuilder getWallet() {
        return exec(
                http("Wallet Service - Get Wallet")
                        .get("http://localhost:9091/api/v1/wallets/user/#{userId}")
                        .header("Authorization", "Bearer #{authToken}")
                        .check(status().in(200))
        );
    }
}
