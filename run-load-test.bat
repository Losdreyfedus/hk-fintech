@echo off
echo ==============================================
echo HK-Fintech Load Testing Runner
echo ==============================================

set BASE_URL=http://localhost:9090
set BASE_USERS=10
set DURATION_SECONDS=30
set SPIKE_USERS=200

echo Select the simulation to run:
echo 1 - Baseline Simulation (Normal Load)
echo 2 - Spike Simulation (Sudden Peak)
set /p sim_choice=Enter choice (1 or 2): 

if "%sim_choice%"=="1" (
    echo Running BaselineSimulation...
    cd load-test
    call ..\mvnw.cmd clean gatling:test -Dgatling.simulationClass=com.hk.loadtest.simulations.BaselineSimulation
    cd ..
) else if "%sim_choice%"=="2" (
    echo Running SpikeSimulation...
    cd load-test
    call ..\mvnw.cmd clean gatling:test -Dgatling.simulationClass=com.hk.loadtest.simulations.SpikeSimulation
    cd ..
) else (
    echo Invalid choice. Exit.
)

echo Load test finished!
echo Please check Kibana (http://localhost:5601) to trace X-Correlation-ID logs.
pause
