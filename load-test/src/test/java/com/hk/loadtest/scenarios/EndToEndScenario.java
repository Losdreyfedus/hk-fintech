package com.hk.loadtest.scenarios;

import com.hk.loadtest.requests.IdentityRequests;
import com.hk.loadtest.requests.WalletRequests;
import com.hk.loadtest.requests.InvoiceRequests;
import io.gatling.javaapi.core.ScenarioBuilder;
import static io.gatling.javaapi.core.CoreDsl.*;

public class EndToEndScenario {

    public static ScenarioBuilder e2eFlow() {
        return scenario("End to End Load Test Scenario")
                .exec(IdentityRequests.login())
                .pause(1)
                .exec(IdentityRequests.validate())
                .pause(1)
                .exec(WalletRequests.getWallet())
                .pause(1)
                .exec(InvoiceRequests.createInvoice())
                .pause(1)
                .exec(InvoiceRequests.payInvoice());
    }
}
