package com.hk.loadtest.simulations;

import com.hk.loadtest.config.ConfigManager;
import com.hk.loadtest.protocol.HttpProtocolBuilderFactory;
import com.hk.loadtest.scenarios.EndToEndScenario;
import io.gatling.javaapi.core.Simulation;
import static io.gatling.javaapi.core.CoreDsl.*;

public class SpikeSimulation extends Simulation {

    public SpikeSimulation() {
        setUp(
                EndToEndScenario.e2eFlow()
                        .injectOpen(atOnceUsers(ConfigManager.getSpikeUsers()))
        ).protocols(HttpProtocolBuilderFactory.createBaseProtocol());
    }
}
