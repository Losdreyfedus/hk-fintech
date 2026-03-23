package com.hk.loadtest.simulations;

import com.hk.loadtest.config.ConfigManager;
import com.hk.loadtest.protocol.HttpProtocolBuilderFactory;
import com.hk.loadtest.scenarios.EndToEndScenario;
import io.gatling.javaapi.core.Simulation;
import static io.gatling.javaapi.core.CoreDsl.*;

public class BaselineSimulation extends Simulation {

    public BaselineSimulation() {
        setUp(
                EndToEndScenario.e2eFlow()
                        .injectOpen(rampUsers(ConfigManager.getBaseUsers())
                                .during(ConfigManager.getDurationSeconds()))
        ).protocols(HttpProtocolBuilderFactory.createBaseProtocol());
    }
}
