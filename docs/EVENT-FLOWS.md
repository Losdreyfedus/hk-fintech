# Event Flows & Process Diagrams

> Complete documentation of all asynchronous event flows in HK Fintech Platform

## Event Flows Overview

### 1. User Registration & Wallet Creation

```
┌──────────────────────────────┐
│ CLIENT: Register User        │
│ POST /auth/register          │
│ {email, password, phone}     │
└────────────┬─────────────────┘
             │
             ▼
    ┌────────────────────────┐
    │ IDENTITY SERVICE       │
    ├────────────────────────┤
    │ 1. Validate input      │
    │ 2. Hash password       │
    │ 3. Create user record  │
    │ 4. Save to user_db     │
    └────────────┬───────────┘
                 │
                 ▼
         ┌──────────────────────┐
         │ Outbox Table Entry   │
         ├──────────────────────┤
         │ Topic: user-         │
         │   created-topic      │
         │ Payload: userId,     │
         │   email, phone       │
         │ Status: PENDING      │
         └────────────┬─────────┘
                      │
      ┌───────────────┴────────────────┐
      │                                │
      ▼                                ▼
  ┌──────────────┐            ┌──────────────────┐
  │ Identity     │            │ Wallet Service   │
  │ Response:    │            │ Consumer:        │
  │ 201 Created  │            │ KafkaListener    │
  └──────────────┘            ├──────────────────┤
                              │ 1. Listen to     │
  (OutboxPublisher)           │    user-created- │
  ┌──────────────┐            │    topic         │
  │ Every 1 sec: │            │ 2. Create wallet │
  │ Publish from │            │ 3. Set initial   │
  │ outbox table │            │    balance = 0   │
  │ → Kafka      │            │ 4. Save to       │
  └──────────────┘            │    wallet_db     │
       │                       └──────────────────┘
       │
       ▼ (Guaranteed delivery)
    Kafka Topic: user-created-topic
        │
        ├─ Partition 0: [Event 1...]
        └─ Partition 1: [Event 2...]

Timeline:
T0:     User submits registration
T0+100ms: User created in Identity DB → 201 response
T0+1000ms: OutboxPublisher publishes → Kafka
T0+1100ms: Wallet Service consumes → Wallet created
```

**Key Points**:
- ✅ User registration is fast (returns immediately)
- ✅ Wallet creation happens asynchronously (eventual consistency)
- ✅ Outbox guarantees no message loss
- ✅ Complete within 1-2 seconds

---

### 2. Top-Up Payment Flow

```
┌────────────────────────────────────┐
│ CLIENT: Request Top-Up              │
│ POST /payments/top-up               │
│ {amount: 100, cardId: 1}            │
└────────────┬───────────────────────┘
             │
             ▼
     ┌──────────────────────────────┐
     │ PAYMENT SERVICE              │
     ├──────────────────────────────┤
     │ Handler: PaymentController   │
     │                              │
     │ 1. Extract userId from JWT   │
     │ 2. BEGIN TRANSACTION         │
     │    ├─ Validate user exists   │
     │    │  (REST → Identity)      │
     │    │                         │
     │    ├─ Validate card exists   │
     │    │  (REST → Card Service)  │
     │    │                         │
     │    ├─ Create payment record  │
     │    │  INSERT payment         │
     │    │  SET status = PENDING   │
     │    │                         │
     │    ├─ Insert outbox entry    │
     │    │  INSERT outbox          │
     │    │  (topic, payload)       │
     │    └─ COMMIT                 │
     │ 3. Return immediate response │
     │                              │
     └────────────┬─────────────────┘
                  │
         ┌────────┴────────┐
         │                 │
         ▼                 ▼
    ┌──────────┐      ┌────────────┐
    │Response: │      │ Outbox     │
    │{         │      │ Table:     │
    │  id: 1,  │      │ ┌────────┐ │
    │  status: │      │ │id|topic│ │
    │  PENDING,│      │ ├────────┤ │
    │  amount: │      │ │1|topup-│ │
    │  100     │      │ │ |compl.│ │
    │}         │      │ │2|topup-│ │
    │          │      │ │ |compl.│ │
    │HTTP 202  │      │ └────────┘ │
    │ACCEPTED  │      └────────────┘
    └──────────┘            │
                            │
                (Every 1 second, OutboxPublisher checks)
                            │
                   PUBLISHED TO KAFKA
                            │
     ┌──────────────────────┴──────────────────────┐
     │                                             │
     ▼                                             ▼
 ┌──────────────────┐                      ┌──────────────────┐
 │ WALLET SERVICE   │                      │ INVOICE SERVICE  │
 │ Consumer Group:  │                      │ Consumer Group:  │
 │ wallet-group-v1  │                      │ invoice-group-v1 │
 │                  │                      │                  │
 │ @KafkaListener   │                      │ @KafkaListener   │
 │ topics=[topup-   │                      │ topics=[topup-   │
 │  completed]      │                      │  completed]      │
 │                  │                      │                  │
 │ 1. Deserialize   │                      │ 1. Parse event   │
 │    event         │                      │ 2. Create        │
 │ 2. Update wallet │                      │    invoice       │
 │    balance += 100│                      │ 3. Link to       │
 │ 3. Log txn       │                      │    payment       │
 │ 4. Publish       │                      │ 4. Set ready     │
 │    wallet-       │                      │    for delivery   │
 │    updated event │                      │ 5. Send email    │
 │                  │                      │    notification   │
 └────────┬─────────┘                      └──────────────────┘
          │
          ▼
    kafka topic:
    wallet-updated-topic

Complete Timeline:
T0:        User submits request
T0+50ms:   Payment validated & saved
T0+100ms:  202 ACCEPTED response sent (User is happy!)
T0+1000ms: OutboxPublisher publishes to Kafka
T0+1050ms: Wallet Service updates balance (eventual)
T0+1100ms: Invoice Service generates invoice (eventual)
T0+1200ms: Email notification sent
[User can see new balance in 1-2 seconds]
```

**Key Flow Control Points**:
| Step | Location | Boundary | Timeout |
|------|----------|----------|---------|
| Validate user | Identity Service | REST/Sync | 100ms |
| Validate card | Card Service | REST/Sync | 100ms |
| Save payment | Payment DB | Tx | 50ms |
| Publish Kafka | OutboxPublisher | Async | 1s |
| Update wallet | Wallet DB | Tx | 100ms |
| Generate invoice | Invoice DB | Tx | 50ms |

---

### 3. Payment Processing & Failure Handling

```
┌────────────────────────┐
│ User Payment Request   │
│ POST /payments         │
│ {toUserId, amount}     │
└───────────┬────────────┘
            │
            ▼
     ┌──────────────────────┐
     │ PAYMENT SERVICE      │
     ├──────────────────────┤
     │ Validate Sender      │
     │ Validate Receiver    │
     │ Check balance        │
     │ Deduct from sender   │
     │ Create payment       │
     └──────────┬───────────┘
                │
    ┌──────────────────────────────────────────┐
    │ If validation FAILS                       │
    └──────────────┬───────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ Payment marked FAILED │
        │ Publish wallet-failed │
        │ topic                │
        └──────────┬───────────┘
                   │
                   ▼ Kafka: wallet-failed-topic
         ┌─────────────────────┐
         │ IDENTITY SERVICE    │
         ├─────────────────────┤
         │ @KafkaListener      │
         │ 1. Log failure      │
         │ 2. Alert user       │
         │ 3. Flag account if  │
         │    suspicious       │
         └─────────────────────┘

    ┌──────────────────────────────────────────┐
    │ If validation SUCCEEDS                    │
    └──────────────────────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ Payment created      │
        │ Publish to           │
        │ payment-completed    │
        │ topic                │
        └──────────┬───────────┘
                   │
                   ▼ Kafka: payment-completed-topic
         ┌──────────────────────────┐
         │ WALLET SERVICE           │
         ├──────────────────────────┤
         │ Receiver wallet listener │
         │ 1. Credit receiver       │
         │ 2. Record transaction    │
         │ 3. Update balance        │
         └──────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │ Sender wallet credit │
        │ if applicable        │
        │ (refund/reversal)    │
        └──────────────────────┘


Error Propagation Example:

Scenario: Insufficient balance detected

Payment        Wallet-Failed Topic
Service        (Event Published)
  │                 │
  ├──────────────────┘
  │
  ├─ wallet-failed-topic
  │  │ Payload: {
  │  │   reason: "INSUFFICIENT_BALANCE",
  │  │   userId: 1,
  │  │   amount: 100,
  │  │   available: 50,
  │  │   timestamp: ...
  │  │ }
  │
  └─ Identity Service Listener
     └─ Create fraud/alert entry in user_profile
     └─ Mark account for review if repeated
     └─ Send notification email to user
```

---

### 4. Complete Microservice Interaction Web

```
                    ┌─────────────────┐
                    │  API GATEWAY    │
                    │(Load Balancer)  │
                    └────────┬────────┘
                             │
                ┌────────────┼────────────┐
                │            │            │
    ┌───────────▼──┐  ┌──────▼────────┐  ┌──────▼─────────┐
    │   IDENTITY   │  │   PAYMENT     │  │     CARD       │
    │   SERVICE    │  │   SERVICE     │  │     SERVICE    │
    │              │  │               │  │                │
    │ • Register   │  │ • Process pay │  │ • Create card  │
    │ • Login      │  │ • Top-up      │  │ • List cards   │
    │ • Validate   │  │ • Validate    │  │ • Rate limit   │
    │   user       │  │   address     │  │                │
    └──┬───┬───────┘  └───┬────┬──────┘  └────────────────┘
       │   │              │    │
       │   │          ┌────▼────▼──────────┐
       │   │          │  WALLET SERVICE    │
       │   │          │                    │
       │   │          │ • Balance mgmt     │
       │   └──────────│ • Top-up handling  │
       │              │ • Transfers        │
       └──────────────│ • History          │
                      └────┬────┬─────────┘
                           │    │
                ┌──────────┘    │
                │               │
            ┌───▼──────────┐    │
            │KAFKA CLUSTER │    │
            │              │    │
            │Topics:       │    └──────────────┐
            │•user-created │                   │
            │•payment-done │                   │
            │•topup-done   │                   │
            │•wallet-fail  │                   │
            │•invoice-gen  │                   │
            └──────┬───────┘                   │
                   │                           │
        ┌──────────┘                           │
        │                                      │
        ▼                               ┌──────▼─────────┐
    ┌──────────────────┐                │   INVOICE      │
    │  ELASTICSEARCH   │                │   SERVICE      │
    │                  │                │                │
    │ Centralized Logs │                │ • Generate     │
    │ Real-time        │                │   invoices     │
    │ dashboards       │                │ • Delivery     │
    │                  │                │                │
    └──────────────────┘                └────────────────┘

Synchronous Communication (REST):
  - Identity ←→ Card (validate user exists)
  - Payment ←→ Identity (validate user)
  - Payment ←→ Card (validate card)

Asynchronous Communication (Kafka):
  - Identity → Topics → Wallet (on user creation)
  - Payment → Topics → Wallet (on payment completion)
  - Payment → Topics → Invoice (on payment)
  - Wallet → Topics → Identity (on failures)
```

---

### 5. Outbox Pattern - Guaranteed Message Delivery

```
┌─ Without Outbox (DANGEROUS) ────────────────────┐
│                                                  │
│ Service Code:                                   │
│   1. Update DB                                  │
│   2. Send to Kafka                              │
│                                                  │
│ ┌────────────────────────────────────────────┐  │
│ │ PROBLEM SCENARIO                           │  │
│ ├────────────────────────────────────────────┤  │
│ │ T1: INSERT payment = OK ✓                 │  │
│ │ T2: kafkaTemplate.send() called...         │  │
│ │ T3: SERVICE CRASHES before send completes │  │
│ │ T4: No message in Kafka ✗                 │  │
│ │ T5: Service restarts                       │  │
│ │                                            │  │
│ │ RESULT: Inconsistent state                 │  │
│ │ DB has payment, Kafka doesn't have it      │  │
│ │ Listeners never know about payment        │  │
│ │ Balance never updates ❌❌❌                │  │
│ └────────────────────────────────────────────┘  │
└────────────────────────────────────────────────┘

┌─ With Outbox Pattern (SAFE) ───────────────────┐
│                                                  │
│ Database Schema:                                │
│ ┌────────────────────────────────┐              │
│ │ OUTBOX TABLE                   │              │
│ ├────────────────────────────────┤              │
│ │ id | topic | payload | proceed │              │
│ ├────────────────────────────────┤              │
│ │ 1 | topup-completed | {...} | F              │
│ │ 2 | topup-completed | {...} | F              │
│ │ 3 | payment-completed | {...} | T            │
│ └────────────────────────────────┘              │
│                                                  │
│ Step 1: ATOMIC Write Transaction               │
│ ┌────────────────────────────────┐              │
│ │ BEGIN TRANSACTION               │              │
│ │                                 │              │
│ │ INSERT payment → payment_table   │              │
│ │       ↓                          │              │
│ │ INSERT outbox record             │              │
│ │       ↓                          │              │
│ │ COMMIT (all or nothing)          │              │
│ └────────────────────────────────┘              │
│                                                  │
│ Result: Both succeed or both fail              │
│ No inconsistent state possible!                │
│                                                  │
│ Step 2: Asynchronous Publishing                │
│ ┌────────────────────────────────┐              │
│ │ OutboxPublisher Scheduler       │              │
│ │ (runs every 1 second)           │              │
│ │                                 │              │
│ │ 1. SELECT * FROM outbox         │              │
│ │    WHERE processed = FALSE      │              │
│ │                                 │              │
│ │ 2. FOR EACH message:            │              │
│ │    kafkaTemplate.send()         │              │
│ │                                 │              │
│ │ 3. UPDATE outbox                │              │
│ │    SET processed = TRUE         │              │
│ │                                 │              │
│ │ 4. If Kafka down? No problem!   │              │
│ │    Message stays in outbox      │              │
│ │    Next run will retry          │              │
│ │    (exponential backoff)        │              │
│ └────────────────────────────────┘              │
│                                                  │
│ This guarantees:                                │
│ ✓ No message loss                               │
│ ✓ At-least-once delivery                        │
│ ✓ Handles service failures                      │
│ ✓ Kafka downtime tolerant                       │
│ ✓ Automatic retry logic                         │
└────────────────────────────────────────────────┘

Code Implementation:

// 1. Transactional Write
@Transactional
public PaymentResponse createPayment(CreatePaymentRequest request, Long userId) {
    // ALL in same transaction
    Payment payment = paymentRepository.save(
        new Payment(userId, request.getAmount(), "PENDING")
    );
    
    outboxRepository.save(
        new Outbox(
            "topup-completed-topic",
            objectMapper.writeValueAsString(new TopUpEvent(payment.getId(), userId))
        )
    );
    
    return new PaymentResponse(payment);// 2. Reliable Publisher
@Service
@RequiredArgsConstructor
public class OutboxPublisher {
    
    @Scheduled(fixedDelay = 1000) // Every 1 sec
    public void publishPendingMessages() {
        List<Outbox> pending = outboxRepository.findByProcessedFalse();
        
        for (Outbox msg : pending) {
            try {
                kafkaTemplate.send(msg.getTopic(), msg.getPayload());
                outboxRepository.markAsProcessed(msg.getId());
                log.info("Published: {}", msg.getId());
            } catch (Exception e) {
                // Will retry next cycle
                log.warn("Publish failed, will retry", e);
            }
        }
    }
}
```

---

## Event Failure Scenarios

### Scenario 1: Kafka Broker Down

```
Timeline:
T0: Payment created + Outbox entry saved
T0+1s: OutboxPublisher tries to publish
T0+1s: KafkaException: Connection refused
       ↓
       (OutboxPublisher logs warning, continues)
       
T0+2s: Retry attempt → Still down
T0+3s: Retry attempt → Still down
...
T5: Kafka broker comes back online
T6: OutboxPublisher successfully publishes
    ├─ Wallet Service receives and updates balance
    └─ Invoice Service generates invoice
    
Result: ✅ Complete success, just delayed

Key: Message is NOT lost, just delayed until Kafka recovers
```

### Scenario 2: Consumer Service Down

```
Kafka: payment-completed-topic
├─ Message published by Payment Service
├─ [Message 1, Message 2, Message 3, ...]
└─ Offset: [0, 1, 2, ...] 

Wallet Service Consumer Group:
├─ Status: DOWN (no instances running)
├─ Last successfully processed offset: 2
└─ New messages queued in Kafka

When Wallet Service comes back online:
├─ Consumer group starts
├─ Fetches next unprocessed messages (offset 3+)
├─ Processes messages in order
└─ Updates wallet balance

Result: ✅ No message loss, automatic recovery
```

### Scenario 3: Duplicate Message Processing

```
Possible cause: Consumer processes message, 
  offset commit fails,
  service restarts

Solution: Implement Idempotent Consumers

@KafkaListener(topics = "topup-completed-topic")
public void handle(TopUpEvent event) {
    // Check if already processed
    if (walletRepository.existsByTransactionId(event.getTransactionId())) {
        log.warn("Duplicate message, skipping: {}", event.getTransactionId());
        return;
    }
    
    // Process only if new
    walletRepository.creditWallet(event.getUserId(), event.getAmount());
    
    // Mark as processed
    walletRepository.recordProcessedTransaction(event.getTransactionId());
}
```

---

## Event Schema & Contract

### Topic: topup-completed-topic

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "TopUpCompletedEvent",
  "properties": {
    "topupId": {
      "type": "integer",
      "description": "Unique topup transaction ID"
    },
    "userId": {
      "type": "integer",
      "description": "User who initiated the top-up"
    },
    "amount": {
      "type": "number",
      "format": "decimal",
      "description": "Amount topped up"
    },
    "currency": {
      "type": "string",
      "enum": ["USD", "EUR", "TRY"],
      "description": "Currency code"
    },
    "source": {
      "type": "string",
      "enum": ["CREDIT_CARD", "BANK_TRANSFER", "WIRE"],
      "description": "Source of funds"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "ISO 8601 timestamp"
    },
    "metadata": {
      "type": "object",
      "properties": {
        "correlationId": {
          "type": "string",
          "description": "For distributed tracing"
        },
        "userId": {
          "type": "string",
          "description": "Service executing"
        }
      }
    }
  },
  "required": ["topupId", "userId", "amount", "currency", "timestamp"]
}
```

### Topic: payment-completed-topic

```json
{
  "type": "object",
  "properties": {
    "paymentId": { "type": "integer" },
    "fromUserId": { "type": "integer" },
    "toUserId": { "type": "integer" },
    "amount": { "type": "number" },
    "paymentMethod": {
      "enum": ["CARD", "WALLET_TRANSFER", "BANK"]
    },
    "status": {
      "enum": ["SUCCESS", "FAILED", "PENDING"]
    },
    "timestamp": { "type": "string", "format": "date-time" }
  }
}
```

---

## Best Practices for Event Handling

1. ✅ **Always make consumers idempotent**
   - Process same message multiple times safely
   - Use `transactionId` as deduplication key

2. ✅ **Use correlation IDs**
   - Track requests across services
   - Useful for debugging end-to-end flows

3. ✅ **Log extensively**
   - Log when consuming
   - Log processing start/end
   - Include event metadata

4. ✅ **Order matters**
   - For same user, process messages in order
   - Use same partition key (userId)
   - Kafka guarantees ordering per partition

5. ✅ **Handle failures gracefully**
   - Catch exceptions
   - Log errors
   - Let retry mechanism handle it
   - Don't fail hard, fail soft

```java
@KafkaListener(topics = "topup-completed-topic", groupId = "wallet-group-v1")
public void handleTopUpCompleted(TopUpEvent event, Acknowledgment ack) {
    try {
        log.info("Processing topup event: id={}, userId={}", 
                 event.getTopupId(), event.getUserId());
        
        // Idempotent processing
        walletService.creditWallet(event);
        
        // Manual commit only on success
        ack.acknowledge();
        
        log.info("Successfully processed topup: {}", event.getTopupId());
    } catch (Exception e) {
        // Log but don't ack - will be retried
        log.error("Failed processing topup: {}", event.getTopupId(), e);
        // Spring will automatically retry or send to DLT (Dead Letter Topic)
    }
}
```

