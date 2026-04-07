# Observability & Monitoring Guide

> Complete guide to monitoring, logging, metrics, and tracing in HK Fintech

## Table of Contents

1. [Logging with ELK Stack](#logging-with-elk-stack)
2. [Metrics & Health Checks](#metrics--health-checks)
3. [Distributed Tracing](#distributed-tracing)
4. [Alerting Strategy](#alerting-strategy)
5. [Dashboards & Visualization](#dashboards--visualization)
6. [Troubleshooting Guide](#troubleshooting-guide)

---

## Logging with ELK Stack

### Architecture

```
┌─────────────────────┐
│ Microservices       │
│ (Spring Boot Apps)  │
│                     │
│ ├─ CardService      │
│ ├─ PaymentService   │
│ ├─ WalletService    │
│ └─ InvoiceService   │
└──────────┬──────────┘
           │
         Logs (JSON formatted)
           │
           ▼
       ┌─────────────┐
       │  Logstash   │  Input: TCP/UDP
       │             │  Filter: Parse & Enrich
       │             │  Output: Elasticsearch
       └─────────────┘
           │
           ▼
   ┌───────────────────┐
   │ Elasticsearch     │
   │                   │
   │ Indexes:          │
   │ • logs-2024.04.07 │
   │ • logs-2024.04.06 │
   │ (Daily rotation)  │
   └─────────────────┘
           │
           ▼
     ┌─────────────┐
     │  Kibana     │
     │  Dashboard  │
     │  & Search   │
     └─────────────┘
```

### Logging Configuration

#### Spring Boot Application Setup

```yaml
# application.yml

spring:
  application:
    name: payment-service
  
  logging:
    level:
      root: INFO
      com.hk.paymentservice: DEBUG
      org.springframework.web: INFO
      org.hibernate: WARN
    
    # JSON logging format for easy parsing
    pattern:
      console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
    
    logback:
      rollingpolicy:
        max-file-size: 100MB
        max-history: 30

# For Logstash integration
logstash:
  sockethost: logstash
  socketport: 5000
  version: 1

# Application context
application:
  version: "1.0.0"
  environment: "production"
  region: "EU"
```

#### Logback Configuration (logback-spring.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Spring Boot automatic configuration first -->
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    
    <!-- Application name from Spring context -->
    <springProperty scope="context" name="APP_NAME" source="spring.application.name"/>
    <springProperty scope="context" name="APP_PORT" source="server.port" defaultValue="8080"/>
    
    <!-- Console appender for development -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"app_version":"1.0.0","environment":"prod"}</customFields>
        </encoder>
    </appender>
    
    <!-- File appender with rolling policy -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/${APP_NAME}.log</file>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${APP_NAME}","port":"${APP_PORT}"}</customFields>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>logs/${APP_NAME}-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
    </appender>
    
    <!-- Async appender for performance -->
    <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <appender-ref ref="FILE"/>
    </appender>
    
    <!-- Root logger configuration -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="ASYNC"/>
    </root>
    
    <!-- Service-specific loggers -->
    <logger name="com.hk.paymentservice" level="DEBUG" additivity="false">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="ASYNC"/>
    </logger>
    
    <logger name="org.springframework.kafka" level="INFO"/>
    <logger name="org.hibernate.SQL" level="DEBUG"/>
</configuration>
```

### Log Levels & When to Use

| Level | Use Case | Example |
|-------|----------|---------|
| `DEBUG` | Detailed info for developers | DB queries, variable values |
| `INFO` | General flow events | Service started, request received |
| `WARN` | Potential issues | Slow query, rate limit approaching |
| `ERROR` | Error conditions | Payment failed, DB unavailable |
| `FATAL` | Critical failures | Complete service failure |

### Structured Logging Examples

```java
@RestController
@RequiredArgsConstructor
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestBody PaymentRequest request,
            @AuthenticationPrincipal Long userId) {
        
        // START LOG with context
        log.info("Creating payment", 
            "userId", userId,
            "amount", request.getAmount(),
            "cardId", request.getCardId()
        );
        
        try {
            PaymentResponse response = paymentService.createPayment(request, userId);
            
            // SUCCESS LOG
            log.info("Payment created successfully",
                "paymentId", response.getId(),
                "status", response.getStatus(),
                "duration_ms", duration
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (ValidationException e) {
            // VALIDATION ERROR (expected failures)
            log.warn("Payment validation failed",
                "userId", userId,
                "reason", e.getMessage()
            );
            return ResponseEntity.badRequest().build();
            
        } catch (Exception e) {
            // UNEXPECTED ERROR
            log.error("Payment creation failed unexpectedly",
                "userId", userId,
                "error", e.getMessage(),
                "exception", e
            );
            return ResponseEntity.internalServerError().build();
        }
    }
}
```

### Kibana Dashboard Setup

#### 1. Access Kibana

```
🔗 http://localhost:5601
Default credentials: elastic / changeme
```

#### 2. Create Index Pattern

```
Dashboard → Index Patterns → Create
Index pattern: logs-*
Time field: @timestamp
```

#### 3. Query Examples

**Find all payment errors in last hour:**
```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "service": "payment-service" } },
        { "match": { "level": "ERROR" } },
        { "range": { "@timestamp": { "gte": "now-1h" } } }
      ]
    }
  }
}
```

**Track payment latency:**
```json
{
  "query": {
    "match": { "message": "Payment created successfully" }
  },
  "aggs": {
    "avg_duration": {
      "avg": { "field": "duration_ms" }
    }
  }
}
```

**Find failed wallet updates:**
```json
{
  "query": {
    "bool": {
      "must": [
        { "match": { "topic": "wallet-failed-topic" } },
        { "range": { "@timestamp": { "gte": "now-24h" } } }
      ]
    }
  }
}
```

---

## Metrics & Health Checks

### Spring Boot Actuator

#### Health Endpoints

```bash
# Overall application health
curl http://localhost:8084/actuator/health
```

Response:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": {...} },
    "kafka": { "status": "UP", "details": {...} },
    "diskSpace": { "status": "UP" }
  }
}
```

#### Health Indicators to Monitor

| Indicator | What It Checks | Action if DOWN |
|-----------|----------------|----------------|
| `db` | Database connectivity | Can't process payments |
| `kafka` | Kafka broker availability | Events won't publish |
| `diskSpace` | Available disk space | Logs might fill up |
| `livenessState` | App is running | K8s will kill pod |
| `readinessState` | App ready for traffic | K8s stops sending requests |

#### Custom Health Indicator

```java
@Component
public class PaymentServiceHealth implements HealthIndicator {
    
    @Override
    public Health health() {
        long pendingPayments = paymentRepository.countByStatus(PENDING);
        
        if (pendingPayments > 10000) {
            return Health.down()
                .withDetail("reason", "Too many pending payments")
                .withDetail("pending_count", pendingPayments)
                .build();
        }
        
        return Health.up()
            .withDetail("pending_payments", pendingPayments)
            .withDetail("processing_time_ms", 250)
            .build();
    }
}

// Access at: http://localhost:8084/actuator/health/paymentService
```

### Key Metrics to Track

```bash
# JVM Memory
curl http://localhost:8084/actuator/metrics/jvm.memory.used
curl http://localhost:8084/actuator/metrics/jvm.memory.max

# HTTP Requests
curl "http://localhost:8084/actuator/metrics/http.server.requests?tag=method:POST"

# Business Metrics
curl http://localhost:8084/actuator/metrics/payment.processed
curl http://localhost:8084/actuator/metrics/wallet.credited
```

### Micrometer Metrics (Future)

```java
@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
}

// In service
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final MeterRegistry meterRegistry;
    
    public void processPayment(PaymentRequest request) {
        try {
            // Business logic...
            
            // Track custom metric
            meterRegistry.counter(
                "payment.processed",
                "status", "success",
                "method", request.getPaymentMethod()
            ).increment();
            
        } catch (Exception e) {
            meterRegistry.counter(
                "payment.processed",
                "status", "failure",
                "reason", e.getClass().getSimpleName()
            ).increment();
        }
    }
}
```

---

## Distributed Tracing

### The Problem: Request Tracing Across Services

```
User Request
    │
    ├─ Payment Service (trace_id=abc123)
    │  └─ Calls Identity Service
    │
    └─ If we don't have tracing:
       "Did the request get to Identity Service?"
       "How long did it take?"
       "Did it fail?"
       → Cannot answer efficiently
```

### Solution: Distributed Tracing with Spring Cloud Sleuth + Jaeger

#### Setup (Future Implementation)

```yaml
# pom.xml dependency
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-otel-auto</artifactId>
</dependency>

<dependency>
    <groupId>io.opentelemetry.exporter</groupId>
    <artifactId>opentelemetry-exporter-jaeger</artifactId>
</dependency>
```

#### Configuration

```yaml
spring:
  sleuth:
    trace-id128: true  # 128-bit trace IDs
    sampler:
      probability: 0.1  # Sample 10% of requests
  
  # OTLP (OpenTelemetry Protocol)
  openobservability:
    exporter:
      otlp:
        endpoint: http://jaeger:4317
```

#### Usage

```java
@RestController
@RequiredArgsConstructor
public class PaymentController {
    
    @PostMapping("/payments")
    public PaymentResponse createPayment(
        @RequestBody PaymentRequest request,
        @AuthenticationPrincipal Long userId
    ) {
        // Sleuth automatically:
        // 1. Generates trace_id (first time)
        // 2. Generates span_id for this request
        // 3. Logs in format: [trace_id,span_id]
        
        log.info("Creating payment"); 
        // Logs: [abc123,def456] Creating payment
        
        // Calling another service - trace continues
        identityClient.validateUser(userId);
        // New span created for this call
        // But same trace_id propagated
        
        return response;
    }
}

// In logs, you'll see:
// 2024-04-07 10:15:32 [app1:abc123:def456:-] INFO Creating payment
// 2024-04-07 10:15:33 [app1:abc123:xyz789:-] INFO Validating user
// Same abc123 trace_id across both!
```

#### Jaeger Dashboard

```
http://localhost:6831 (Jaeger UI)

Search for:
- Service: payment-service
- Operation: POST /payments
- Tags: error=true

View:
- Timeline of all spans
- Service dependencies
- Latency breakdown
- Error traces
```

---

## Alerting Strategy

### Alert Rules

```yaml
# Prometheus alert rules (prometheus-rules.yml)

groups:
  - name: payment-service
    interval: 30s
    rules:
      # Alert 1: Service Down
      - alert: PaymentServiceDown
        expr: up{job="payment-service"} == 0
        for: 1m
        annotations:
          summary: "Payment Service is down!"
          description: "Payment service {{ $labels.instance }} is not responding"
          action: "Check logs, restart service"
      
      # Alert 2: High Error Rate
      - alert: HighPaymentErrorRate
        expr: |
          (rate(http_requests_total{status=~"5..", endpoint="/payments"}[5m])) /
          (rate(http_requests_total{endpoint="/payments"}[5m])) > 0.05
        for: 5m
        annotations:
          summary: "Payment error rate > 5%"
          action: "Check payment service logs and DB connectivity"
      
      # Alert 3: Slow Payments
      - alert: SlowPaymentProcessing
        expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{endpoint="/payments"}[5m])) > 5
        annotations:
          summary: "95th percentile payment latency > 5 seconds"
          action: "Check database performance, Kafka lag"
      
      # Alert 4: Kafka Lag High
      - alert: KafkaConsumerLagHigh
        expr: kafka_consumer_lag{consumer_group="wallet-group-v1"} > 1000
        annotations:
          summary: "Wallet consumer is 1000+ messages behind"
          action: "Increase consumer threads, check processing logic"
      
      # Alert 5: Database Connection Pool Exhausted
      - alert: DBConnectionPoolExhausted
        expr: |
          (hikaricp_connections_pending{pool="paymentDB"} / 
           hikaricp_connections_max{pool="paymentDB"}) > 0.9
        annotations:
          summary: "Payment DB connection pool at 90% capacity"
          action: "Increase pool size or reduce connections"
```

### Alert Routing (To Slack/PagerDuty)

```yaml
# alertmanager-config.yml

global:
  slack_api_url: "https://hooks.slack.com/services/XXXX/YYYY"
  pagerduty_url: "https://events.pagerduty.com/v2/enqueue"

route:
  receiver: "default"
  group_by: ["alertname", "service"]
  group_wait: 30s
  repeat_interval: 3h
  
  routes:
    # Critical alerts → Immediate PagerDuty
    - match:
        severity: critical
      receiver: pagerduty
      group_wait: 0s
      repeat_interval: 30m
    
    # Warning → Slack
    - match:
        severity: warning
      receiver: slack
      group_wait: 5m

receivers:
  - name: pagerduty
    pagerduty_configs:
      - service_key: "YOUR_SERVICE_KEY"
        description: "{{ .GroupLabels.alertname }}"
  
  - name: slack
    slack_configs:
      - channel: "#alerts"
        title: "{{ .GroupLabels.alertname }}"
        text: "{{ range .Alerts.Firing }}{{ .Annotations.summary }}{{ end }}"
```

---

## Dashboards & Visualization

### 1. System Health Dashboard

```
┌─────────────────────────────────────────────────────┐
│  HK FINTECH SYSTEM HEALTH DASHBOARD                 │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Service Status:                                     │
│  ├─ ✅ Identity Service   UP (8081)                │
│  ├─ ✅ Payment Service    UP (8084)                │
│  ├─ ✅ Card Service       UP (8083)                │
│  ├─ ✅ Wallet Service     UP (8082)                │
│  └─ ⚠️  Invoice Service   DEGRADED                 │
│         └─ Database slow: 3s average latency       │
│                                                      │
│  Kafka Status:                                       │
│  ├─ Brokers: 1/1 UP                                │
│  ├─ Consumer Lag:                                   │
│  │  ├─ wallet-group-v1: 0 messages                 │
│  │  ├─ invoice-group-v1: 48 messages               │
│  │  └─ identity-group: 0 messages                  │
│  └─ Topics: 5 topics, 50 partitions               │
│                                                      │
│  Database Status:                                    │
│  ├─ payment_db: ✅ UP (conn pool 8/20)            │
│  ├─ wallet_db:  ✅ UP (conn pool 5/20)            │
│  ├─ card_db:    ✅ UP (conn pool 3/20)            │
│  └─ identity_db: ✅ UP (conn pool 2/20)           │
│                                                      │
│  Traffic (last 1h):                                 │
│  ├─ Total Requests: 125,432                        │
│  ├─ Error Rate: 0.32%                              │
│  ├─ Avg Latency: 245ms                             │
│  └─ p99 Latency: 1.2s                              │
│                                                      │
│  Alerts (Active):                                    │
│  └─ ⚠️  Invoice Service Degraded (since 5 min)     │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 2. Payment Processing Dashboard

```
Metrics:
├─ Payments Created (1h): 1,245
├─ Payments Completed: 1,210 (97.2%)
├─ Payments Failed: 35 (2.8%)
├─ Avg Processing Time: 450ms
└─ p99 Processing Time: 2.1s

Charts:
├─ Payload Success Rate (time series)
├─ Processing Time Distribution (histogram)
├─ Payment Method Breakdown (pie chart)
│  ├─ Credit Card: 65%
│  ├─ Wallet Transfer: 25%
│  └─ Bank Transfer: 10%
└─ Top Error Reasons (bar chart)
   ├─ Insufficient Funds: 40%
   ├─ Invalid Card: 35%
   └─ Rate Limited: 25%
```

### 3. Kafka Health Dashboard

```
Topics Overview:
├─ topup-completed-topic
│  ├─ Partitions: 3
│  ├─ Replication Factor: 1
│  ├─ Messages Today: 15,432
│  └─ Size: 245MB
│
├─ payment-completed-topic
│  └─ Messages Today: 1,245
│
├─ wallet-updated-topic
│  └─ Messages Today: 1,210
│
└─ wallet-failed-topic
   └─ Messages Today: 35

Consumer Groups:
├─ wallet-group-v1
│  ├─ Members: 3 (all healthy)
│  ├─ Lag: 0 (in sync)
│  └─ Last Offset: 15,432
│
├─ invoice-group-v1
│  ├─ Members: 2
│  ├─ Lag: 48 (needs attention)
│  └─ Last Offset: 15,384
│
└─ identity-group
   ├─ Members: 1
   ├─ Lag: 0
   └─ Last Offset: 5,210
```

---

## Troubleshooting Guide

### Issue: High Latency in Payment Processing

**Symptoms**: 
- Payment creation takes > 2 seconds
- Users complaining about slow response

**Investigation Steps**:

```bash
# Step 1: Check service logs
docker-compose logs -f payment-service | grep -i "slow\|duration"

# Step 2: Check database performance
docker-compose exec postgres-payment psql -U admin -d payment_db
> SELECT query, mean_time FROM pg_stat_statements 
  ORDER BY mean_time DESC LIMIT 5;

# Step 3: Check Kafka lag
docker-compose exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group wallet-group-v1 \
  --describe

# Step 4: Monitor CPU/Memory
docker stats payment-service
```

**Common Causes & Fixes**:

| Cause | Fix |
|-------|-----|
| Missing DB index | Add index on frequently queried columns |
| Slow query | Optimize query / add WHERE clause |
| Thread pool exhausted | Increase `tomcat.threads.max` |
| Kafka backlog | Check consumer processing speed |
| Memory leak | Check heap usage via `jps -lmv` |

### Issue: Messages Not Being Processed from Kafka

**Symptoms**:
- Kafka topics have messages
- Consumer group shows lag
- Balance not updating

**Investigation**:

```bash
# Check consumer group status
docker-compose exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group wallet-group-v1 \
  --describe

# Check if consumer is running
docker-compose logs wallet-service | grep "KafkaListener\|started"

# Check for errors
docker-compose logs wallet-service | grep -i "error\|exception"

# Check database connectivity
docker-compose logs wallet-service | grep "connection\|database"
```

**Solutions**:

1. **Restart consumer**:
   ```bash
   docker-compose restart wallet-service
   ```

2. **Reset consumer offset** (start from beginning):
   ```bash
   docker-compose exec kafka kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 \
     --group wallet-group-v1 \
     --reset-offsets \
     --to-earliest \
     --execute
   ```

3. **Check database locks**:
   ```sql
   SELECT * FROM pg_locks WHERE NOT granted;
   ```

### Issue: Database Queries Timing Out

**Symptoms**:
- "Connection timeout" errors
- Random failures in payment processing

**Check Connection Pool**:

```java
// Add monitoring to application
@RestController
public class HealthController {
    
    @GetMapping("/db-pool-status")
    public Map<String, Object> checkDBPool(HikariDataSource ds) {
        return Map.of(
            "active_connections", ds.getHikariPoolMXBean().getActiveConnections(),
            "idle_connections", ds.getHikariPoolMXBean().getIdleConnections(),
            "total_connections", ds.getHikariPoolMXBean().getTotalConnections(),
            "max_connections", ds.getMaximumPoolSize()
        );
    }
}
```

**Fix**:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30        # Increase from 20
      minimum-idle: 5              # Keep idle connections
      idle-timeout: 600000         # 10 minutes
      connection-timeout: 30000    # 30 seconds
```

---

## Recommended Monitoring Stack Evolution

```
Phase 1 (Current):
✅ ELK Stack (Elasticsearch, Logstash, Kibana)
✅ Spring Boot Actuator
✅ Docker logs

Phase 2 (Next 3 months):
➕ Prometheus (metrics collection)
➕ Grafana (advanced visualizations)
➕ Spring Cloud Sleuth (distributed tracing)

Phase 3 (Production):
➕ Jaeger (distributed tracing UI)
➕ Alert Manager (alert routing)
➕ Custom dashboards (business metrics)
```

