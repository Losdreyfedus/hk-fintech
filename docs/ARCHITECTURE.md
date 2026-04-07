# HK Fintech - System Architecture Documentation

> Deep dive into the technical architecture, patterns, and design decisions

## Table of Contents

1. [Core Architecture Patterns](#core-architecture-patterns)
2. [Service Communication](#service-communication)
3. [Data Consistency Strategy](#data-consistency-strategy)
4. [Deployment Architecture](#deployment-architecture)
5. [Security Architecture](#security-architecture)
6. [Performance Considerations](#performance-considerations)

---

## Core Architecture Patterns

### 1. Microservices Architecture

#### Service Isolation

Each service is independently deployable with clear boundaries:

```
┌─────────────────────┐
│ Card Service        │
├─────────────────────┤
│ Controller Layer    │
│ Service Layer       │
│ Repository Layer    │
│ PostgreSQL DB       │
└─────────────────────┘

┌─────────────────────┐
│ Payment Service     │
├─────────────────────┤
│ Controller Layer    │
│ Service Layer       │
│ Repository Layer    │
│ PostgreSQL DB       │
└─────────────────────┘

(Each service owns its data - no shared DB)
```

#### Cross-Service Communication

**Synchronous (REST)**:
- User existence check: CardService → IdentityService
- Card validation: PaymentService → CardService

**Asynchronous (Kafka)**:
- Event publishing: Service → Kafka Topic
- Event consumption: Consumer groups

### 2. Event-Driven Architecture

#### Event Topics & Flow

```
Kafka Cluster
├── user-created-topic
│   └─ Published by: IdentityService
│   └─ Consumed by: WalletService
│   └─ Payload: { userId, email, phone, kycLevel }
│
├── payment-completed-topic
│   └─ Published by: PaymentService
│   └─ Consumed by: WalletService, InvoiceService
│   └─ Payload: { paymentId, userId, amount, timestamp }
│
├── topup-completed-topic
│   └─ Published by: PaymentService
│   └─ Consumed by: WalletService
│   └─ Payload: { topupId, userId, amount, source }
│
├── wallet-updated-topic
│   └─ Published by: WalletService
│   └─ Consumed by: InvoiceService
│   └─ Payload: { walletId, userId, newBalance, reason }
│
└── wallet-failed-topic
    └─ Published by: WalletService
    └─ Consumed by: IdentityService
    └─ Payload: { reason, userId, transactionId }
```

#### Topic Naming Convention

Pattern: `{entity}-{action}-topic`

Examples:
- `user-created-topic` (entity: user, action: created)
- `payment-completed-topic` (entity: payment, action: completed)
- `invoice-generated-topic` (entity: invoice, action: generated)

#### Consumer Groups

```
Kafka Topic: payment-completed-topic
├── Consumer Group: wallet-group-v1
│   └─ Members: 1-3 instances of WalletService
│   └─ Purpose: Update wallet balance
│
└── Consumer Group: invoice-group-v1
    └─ Members: 1-2 instances of InvoiceService
    └─ Purpose: Generate invoice
```

### 3. Outbox Pattern (Guaranteed Delivery)

#### Problem Solved
Without outbox, if service crashes after database write but before Kafka publish:
- Database updated ✅
- Kafka message lost ❌
- Inconsistent distributed state

#### Solution: Write to Outbox Table First

```sql
CREATE TABLE outbox (
  id BIGSERIAL PRIMARY KEY,
  topic VARCHAR(255) NOT NULL,
  payload TEXT NOT NULL,
  aggregateId BIGINT,
  processed BOOLEAN DEFAULT FALSE,
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  publishedAt TIMESTAMP
);
```

#### Flow with Outbox Pattern

```
Service A: Create Payment
    ↓
1. BEGIN TRANSACTION
    ├─ UPDATE payment SET status = 'PENDING'
    └─ INSERT INTO outbox (topic, payload) VALUES (...)
    ↓
2. COMMIT TRANSACTION (all-or-nothing)
    ↓
3. OutboxPublisher @Scheduled(fixedDelay=1000ms)
    ├─ SELECT * FROM outbox WHERE processed = FALSE
    ├─ FOR EACH message:
    │   ├─ KafkaTemplate.send(topic, payload)
    │   └─ UPDATE outbox SET processed = TRUE, publishedAt = NOW()
    ├─ Handle failures gracefully (exponential backoff)
    └─ Log published messages

Service B: Listen to Topic
    └─ @KafkaListener(topics = "payment-completed-topic")
       └─ Update wallet balance
```

#### Implementation in Code

```java
// Payment Service - Publishing
@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    
    public PaymentResponse processPayment(CreatePaymentRequest request, Long userId) {
        // Validate payment
        
        // Save payment and outbox in same transaction
        return transactionTemplate.execute(status -> {
            Payment payment = new Payment(...);
            paymentRepository.save(payment);
            
            // Add to outbox for reliable publishing
            Outbox outbox = new Outbox(
                "payment-completed-topic",
                payment.toJson()
            );
            outboxRepository.save(outbox);
            
            return new PaymentResponse(payment);
        });
    }
}

// OutboxPublisher - Reliable Publishing
@Service
@RequiredArgsConstructor
public class OutboxPublisher {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    
    @Scheduled(fixedDelay = 1000) // Every 1 second
    public void publishPendingMessages() {
        List<Outbox> pendingMessages = outboxRepository.findByProcessedFalse();
        
        for (Outbox message : pendingMessages) {
            try {
                kafkaTemplate.send(message.getTopic(), message.getPayload());
                outboxRepository.markAsProcessed(message.getId());
                log.info("Published outbox message: id={}, topic={}", 
                         message.getId(), message.getTopic());
            } catch (Exception e) {
                log.warn("Failed to publish message: {}", message.getId(), e);
                // Will retry in next scheduled run
            }
        }
    }
}

// Wallet Service - Consuming
@Service
@RequiredArgsConstructor
public class TopUpCompletedConsumer {
    private final WalletService walletService;
    
    @KafkaListener(topics = "topup-completed-topic", groupId = "wallet-group-v1")
    public void handleTopUpCompleted(String message) {
        TopUpEvent event = objectMapper.readValue(message, TopUpEvent.class);
        walletService.creditTopUpAmount(event.getUserId(), event.getAmount());
    }
}
```

### 4. Saga Pattern (Choreography-Based)

#### Pattern Overview

Saga: Long-running transaction across multiple services, coordinated via events.

**Two Implementations**:
1. **Choreography**: Services listen to events (current)
2. **Orchestration**: Central coordinator (future consideration)

#### Example Flow: Complete Top-Up Saga

```
┌─────────────────────────────────────┐
│ USER: Request Top-Up                │
│ POST /payments/top-up               │
└──────────────┬──────────────────────┘
               │
               ▼
        ┌──────────────────┐
        │ PAYMENT SERVICE  │
        ├──────────────────┤
        │ 1. Validate user │ (call Identity Service)
        │ 2. Validate card │ (call Card Service)
        │ 3. Check balance │
        │ 4. Create        │
        │    transaction   │
        │ 5. Publish event │
        └────────┬─────────┘
                 │
    topup-completed-topic
                 │
      ┌──────────┴──────────┐
      │                     │
      ▼                     ▼
  ┌────────────┐      ┌───────────────┐
  │ WALLET     │      │ INVOICE       │
  │ SERVICE    │      │ SERVICE       │
  ├────────────┤      ├───────────────┤
  │ 1. Update  │      │ 1. Create     │
  │    balance │      │    invoice    │
  │ 2. Log     │      │ 2. Link to    │
  │    txn     │      │    payment    │
  │ 3. Publish │      │ 3. Send notif │
  │    event   │      │    (email)    │
  └──────┬─────┘      └───────────────┘
         │
  wallet-updated-topic
         │
         ▼
    ┌──────────┐
    │ IDENTITY │
    │ SERVICE  │
    └──────────┘
    (Update user profile)

Total time: 500-1000ms (async processing)
User sees: 202 Accepted response immediately
```

#### Failure Handling in Saga

**Scenario 1: Wallet Service fails**
```
Payment published to outbox
    ↓
Wallet service down
    ↓
OutboxPublisher retries (exponential backoff)
    ↓
Wallet service recovers
    ↓
Message published successfully
    ↓
Balance eventually updated (eventual consistency)
```

**Scenario 2: Validation fails**
```
Payment validation fails
    ↓
Transaction marked as FAILED in PaymentDB
    ↓
wallet-failed-topic published
    ↓
Identity Service handles failure
    ↓
User notified via notification service
```

---

## Service Communication

### REST API Communication

#### Synchronous Calls (When Needed)

```java
// CardService calling IdentityService
@RestController
@RequiredArgsConstructor
public class CardController {
    private final CardService cardService;
    private final RestTemplate restTemplate;
    
    @PostMapping
    public CardResponse createCard(CreateCardRequest req, @AuthenticationPrincipal Long userId) {
        // Synchronously check if user exists (MUST succeed)
        boolean userExists = restTemplate.getForObject(
            "http://identity-service:8081/api/v1/users/{id}/exists",
            Boolean.class,
            userId
        );
        
        if (!userExists) {
            throw new UserNotFoundException("User not found");
        }
        
        // Proceed with card creation
        return cardService.createCard(req, userId);
    }
}
```

#### API Documentation Template

Every service should document:
- Authentication requirement
- Rate limits
- Request/response format
- Error codes
- Example curl commands

### Kafka Consumer Configuration

```java
@Configuration
public class KafkaConfig {
    
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "wallet-group-v1");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
                  StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
                  StringDeserializer.class);
        
        // Consumer only starts consuming from LATEST offsets on restart
        // Set to EARLIEST if you want to reprocess old messages
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        
        // Commits offset every 10 seconds
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 10000);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Concurrent consumers per partition
        factory.setConcurrency(3);
        
        return factory;
    }
}
```

---

## Data Consistency Strategy

### Database Per Service

#### Benefits
✅ Technology choice per service  
✅ Independent scaling  
✅ Failure isolation  

#### Trade-offs
❌ No ACID across services  
❌ Eventual consistency  
❌ Complex joins impossible  

#### Solution: Aggregated Data

```java
// WalletService needs Card info
// Instead of JOIN, call CardService

@Service
@RequiredArgsConstructor
public class WalletService {
    private final RestTemplate restTemplate;
    private final WalletRepository walletRepository;
    
    // Single DB responsibility: Wallet
    // Card info comes from CardService via REST API
    public WalletWithCardsDTO getWalletWithCards(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId);
        
        // Call CardService to get cards
        List<CardDTO> cards = restTemplate.exchange(
            "http://card-service:8083/api/v1/cards",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<CardDTO>>() {}
        ).getBody();
        
        return new WalletWithCardsDTO(wallet, cards);
    }
}
```

### Eventual Consistency Guarantees

| Scenario | Consistency Level | Time to Consistency |
|----------|-------------------|-------------------|
| User sign-up → Wallet created | Eventual | 1-2 seconds |
| Payment → Balance updated | Eventual | 500ms-2s |
| Payment → Invoice generated | Eventual | 1-3 seconds |
| Delete user | Strong | Synchronous |

### Dealing with Stale Data

**Problem**: WalletService might not immediately have updated balance

**Solutions**:

1. **Optimistic UI**
   ```javascript
   // Client predicts new balance
   const newBalance = currentBalance - amount;
   updateUI(newBalance);
   
   // Later verify from server
   fetch('/wallet').then(updateUI);
   ```

2. **Database Caching with TTL**
   ```java
   @Cacheable(value = "wallet", key = "#userId", cacheManager = "cacheManager")
   public Wallet getWallet(Long userId) {
       return walletRepository.findByUserId(userId);
   }
   
   // Cache expires after 5 seconds
   @CacheEvict(value = "wallet", key = "#userId")
   @Scheduled(fixedRate = 5000)
   public void invalidateCache() {}
   ```

3. **Polling for Latest State**
   ```java
   public WalletResponse getWalletWithRetry(Long userId) {
       Wallet wallet = walletRepository.findByUserId(userId);
       
       // If balance is stale (compared to local cache), 
       // trigger sync from master
       if (isStale(wallet.getLastUpdated())) {
           // Call master wallet data source
           wallet = syncWithMaster(userId);
       }
       
       return new WalletResponse(wallet);
   }
   ```

---

## Deployment Architecture

### Docker Compose (Local Development)

```yaml
version: '3.8'
services:
  # Infrastructure
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    healthcheck: [kafka coordination]
  
  kafka:
    image: confluentinc/cp-kafka:7.4.0
    depends_on: [zookeeper]
    healthcheck: [broker availability]
  
  # Databases
  postgres-payment:
    image: postgres:15-alpine
    volumes: [persistent storage]
    healthcheck: [db connectivity]
  
  # Services
  payment-service:
    build: [payment-service/]
    depends_on: [postgres-payment, kafka]
    environment: [DB_URL, KAFKA_BROKERS]
    healthcheck: [app health]
```

### Kubernetes (Production)

#### Namespace Isolation

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: hk-fintech
```

#### StatefulSet for Databases

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres-payment
  namespace: hk-fintech
spec:
  serviceName: postgres-payment
  replicas: 1
  selector:
    matchLabels:
      app: postgres-payment
  template:
    metadata:
      labels:
        app: postgres-payment
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: payment_db
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 10Gi
```

#### Deployment for Stateless Services

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  namespace: hk-fintech
spec:
  replicas: 3  # Start with 3 instances
  selector:
    matchLabels:
      app: payment-service
  template:
    metadata:
      labels:
      app: payment-service
    spec:
      containers:
      - name: payment-service
        image: hk-fintech/payment-service:1.0.0
        ports:
        - containerPort: 8084
        env:
        - name: DATABASE_URL
          valueFrom:
            configMapKeyRef:
              name: hk-config
              key: database.url
        - name: KAFKA_BROKERS
          valueFrom:
            configMapKeyRef:
              name: hk-config
              key: kafka.brokers
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8084
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/db
            port: 8084
          initialDelaySeconds: 10
          periodSeconds: 5
```

#### Horizontal Pod Autoscaling

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-service-hpa
  namespace: hk-fintech
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

---

## Security Architecture

### Authentication & Authorization

```
User Login Request
    ↓
Identity Service
    ├─ Validate credentials
    ├─ Generate JWT token
    └─ Return token
    
JWT Token Format:
{
  "header": { "alg": "HS256", "typ": "JWT" },
  "payload": {
    "sub": "userId",
    "email": "user@example.com",
    "roles": ["USER", "PREMIUM"],
    "exp": 1618970400
  },
  "signature": "..."
}

Subsequent requests:
Authorization: Bearer {JWT_TOKEN}
    ↓
Spring Security
    ├─ Extract token from header
    ├─ Validate signature
    ├─ Check expiration
    ├─ Extract userId
    └─ @AuthenticationPrincipal userId available
```

### Rate Limiting

```java
@Service
@RequiredArgsConstructor
public class RateLimitService {
    private final Cache bucketCache;
    
    @Scheduled(fixedDelay = 60000) // Clean expired buckets
    public void cleanExpiredBuckets() {
        bucketCache.invalidateAll();
    }
    
    public Bucket resolveBucket(RateLimitType type, Long userId) {
        // Separate bucket per user and rate limit type
        String key = type.name() + ":" + userId;
        
        return bucketCache.getIfPresent(key, 
            () -> createBucket(type.getCapacity(), type.getRefillRate())
        );
    }
}

public enum RateLimitType {
    CARD_CREATE(5, Duration.ofMinutes(1)),        // 5 requests/min
    CARD_LIST(20, Duration.ofMinutes(1)),          // 20 requests/min
    PAYMENT_CREATE(10, Duration.ofMinutes(1));     // 10 requests/min
    
    private final long capacity;
    private final Duration refillRate;
    
    RateLimitType(long capacity, Duration refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
    }
}
```

### Service-to-Service Communication Security

**Current**: Plain HTTP (for dev)  
**Future**: 
- mTLS (mutual TLS) between services
- Service account tokens
- Network policies in K8s

---

## Performance Considerations

### Database Optimization

```sql
-- Used by PaymentController.getPaymentByUserId()
CREATE INDEX idx_payment_user_created 
ON payment(user_id, created_at DESC);

-- Used by WalletService.getTransactionHistory()
CREATE INDEX idx_outbox_processed_topic 
ON outbox(processed, topic);
```

### Kafka Performance Tuning

```properties
# Producer (any service publishing events)
spring.kafka.producer.batch-size=16384          # Batch messages
spring.kafka.producer.linger-ms=10              # Wait 10ms to batch
spring.kafka.producer.compression-type=snappy   # Compress payloads

# Consumer (listeners)
spring.kafka.consumer.max-poll-records=500      # Fetch up to 500
spring.kafka.consumer.session-timeout-ms=30000  # 30s session
spring.kafka.listener.concurrency=3             # 3 threads per partition
```

### Caching Strategy

```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("wallets", "cards", "users");
    }
}

// Usage in WalletService
@Cacheable(value = "wallets", key = "#userId")
public Wallet getWallet(Long userId) {
    return walletRepository.findByUserId(userId);
}

// Invalidate when updated
@CacheEvict(value = "wallets", key = "#userId")
public void updateWallet(Long userId, BigDecimal balance) {
    walletRepository.updateBalance(userId, balance);
}
```

### Connection Pooling

```properties
# Database Connection Pool (HikariCP)
spring.datasource.hikari.maximum-pool-size=20   # Max connections
spring.datasource.hikari.minimum-idle=5         # Min idle
spring.datasource.hikari.connection-timeout=30000 # 30s timeout
spring.datasource.hikari.idle-timeout=600000    # 10min idle timeout

# Kafka Connection Pool
spring.kafka.producer.acks=1          # Wait for leader only
spring.kafka.bootstrap-servers=kafka:9092
```

---

## Monitoring & Observability

### Health Endpoints

```bash
# Overall health
curl http://localhost:8084/actuator/health

# DB health
curl http://localhost:8084/actuator/health/db

# Kafka connectivity
curl http://localhost:8084/actuator/health/kafkaHealthIndicator

# Detailed
curl http://localhost:8084/actuator/health?show=WHEN_AUTHORIZED
```

### Metrics Collection

```bash
# JVM Memory
curl http://localhost:8084/actuator/metrics/jvm.memory.used

# HTTP Requests
curl http://localhost:8084/actuator/metrics/http.server.requests

# Business Metrics
curl http://localhost:8084/actuator/metrics/payment.processed
```

---

## Appendices

### A. Technology Decision Matrix

| Requirement | Technology | Why |
|-------------|-----------|-----|
| Data Store | PostgreSQL | ACID, JSON support, proven |
| Message Bus | Kafka | High throughput, durability |
| Container | Docker | Portability, consistency |
| Orchestration | Kubernetes | Production-grade, scalable |
| Rate Limiting | Bucket4j | In-memory, no extra service |
| Logging | ELK | Centralized, searchable |

### B. Future Enhancement Paths

1. **Service Mesh (Istio)**
   - Better observability
   - Automatic retries
   - Circuit breaking

2. **API Gateway (Kong/Envoy)**
   - Central authentication
   - Rate limiting at gateway
   - Request routing

3. **Message Queue Redundancy**
   - Kafka replication factor > 1
   - Multi-datacenter setup

4. **Advanced Monitoring**
   - Prometheus metrics
   - Grafana dashboards
   - Jaeger distributed tracing

