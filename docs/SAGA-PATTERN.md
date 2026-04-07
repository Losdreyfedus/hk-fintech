# Saga Pattern - Distributed Transactions

> Deep dive into saga pattern implementation for distributed payment flows

## Saga Pattern Overview

### Problem: Distributed Transactions Across Multiple Databases

In a microservices architecture with Database Per Service pattern:

```
Traditional (Monolith):
┌─────────────────────────┐
│ Application             │
├─────────────────────────┤
│ BEGIN TRANSACTION       │
│  ├─ Update table1       │
│  ├─ Update table2       │
│  ├─ Update table3       │
│ COMMIT (all or ROLLBACK)│
└─────────────────────────┘
└─ Single database: ACID guaranteed ✅

Microservices (Multiple DBs):
Service A          Service B          Service C
│ Table A │        │ Table B │        │ Table C │
│─────────│        │─────────│        │─────────│

NO SHARED TRANSACTION across service boundaries ❌
Need Saga Pattern for eventual consistency ✓
```

### Solution: Saga Pattern

**Definition**: A sequence of local transactions, each updating its database and publishing an event that triggers the next local transaction.

**Two Flavors**:
1. **Choreography** (Event-driven, current implementation)
2. **Orchestration** (Centralized coordinator, future enhancement)

---

## Implementation 1: Choreography-Based Saga (Current)

### Architecture

```
Service 1 does work
    ↓
Publishes event X
    ↓
Service 2 listens to X, does work
    ↓
Publishes event Y
    ↓
Service 3 listens to Y, does work
    ↓
Publishes event Z
    ↓
(Flow complete or trigger compensation)
```

### Example: Payment with Top-Up Saga

```
SAGA: Complete Top-Up Payment

Step 1: Payment Service
┌────────────────────────────────┐
│ User submits top-up request    │
│ Amount: $100                   │
└────────────────────────────────┘
         │
         ▼
┌────────────────────────────────┐
│ PAYMENT SERVICE                │
├────────────────────────────────┤
│ LOCAL TRANSACTION:             │
│ ├─ Validate user               │
│ ├─ Validate card               │
│ ├─ Create payment record       │
│ │  status = PENDING            │
│ └─ INSERT INTO outbox          │
│    (topup-completed-topic)     │
│                                │
│ Publish: TopUpCompletedEvent   │
│ {topupId, userId, amount, ...} │
└────────┬───────────────────────┘
         │
         ▼ Event: TopUpCompleted (Kafka)
      
Step 2: Wallet Service
┌────────────────────────────────┐
│ @KafkaListener                 │
│ topics = topup-completed-topic │
├────────────────────────────────┤
│ LOCAL TRANSACTION:             │
│ ├─ Read current balance        │
│ ├─ Validate amount             │
│ ├─ Update balance              │
│ │  newBalance = old + $100     │
│ ├─ Log transaction             │
│ └─ INSERT INTO outbox          │
│    (wallet-updated-topic)      │
│                                │
│ Publish: WalletUpdatedEvent    │
│ {walletId, userId, newBalance}│
└────────┬───────────────────────┘
         │
         ▼ Event: WalletUpdated (Kafka)

Step 3: Invoice Service
┌────────────────────────────────┐
│ @KafkaListener                 │
│ topics = wallet-updated-topic  │
├────────────────────────────────┤
│ LOCAL TRANSACTION:             │
│ ├─ Parse wallet update event   │
│ ├─ Create invoice record       │
│ ├─ Link to wallet transaction  │
│ ├─ Set status = READY_DELIVERY │
│ └─ Log invoice                 │
│                                │
│ Publish: InvoiceCreatedEvent   │
│ {invoiceId, walletTxnId}       │
└────────────────────────────────┘
         │
         ▼
    ✅ SAGA COMPLETE
```

### State Transitions

```
Payment Service State Machine:
┌─────────┐
│ PENDING │  (initial state)
├─────────┤
│  ├─ User top-up accepted
│  └─ Outbox entry created
└────┬────┘
     │
     ├─ Success → COMPLETED ✅
     │  (WalletUpdated received → mark done)
     │
     └─ Failure → FAILED ❌
        (Timeout OR Wallet Error Event received)

Wallet Service State Machine:
┌──────────────┐
│ NOT_CREDITED │  (initial)
├──────────────┤
│  └─ Listening for TopUpCompleted event
└────┬─────────┘
     │
     ├─ Success → CREDITED ✅
     │  │ Balance updated
     │  └─ WalletUpdated event published
     │
     └─ Failure → FAILED_INVALID ❌
        (Validation error, amount check)
        → Publish wallet-failed-topic
        → Payment marked as FAILED
```

### Choreography Code Example

```java
// ========== PAYMENT SERVICE (Step 1) ==========
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    @Transactional
    public PaymentResponse processTopUp(TopUpRequest request, Long userId) {
        log.info("Starting top-up payment saga for userId={}, amount={}", 
                 userId, request.getAmount());
        
        // LOCAL TRANSACTION START
        try {
            // Step 1: Validate user and card
            identityClient.validateUser(userId);
            cardClient.validateCard(request.getCardId());
            
            // Step 2: Create payment record
            Payment payment = new Payment()
                .setUserId(userId)
                .setAmount(request.getAmount())
                .setCardId(request.getCardId())
                .setStatus(PaymentStatus.PENDING);
            
            Payment savedPayment = paymentRepository.save(payment);
            log.info("Payment created with id={}, status=PENDING", savedPayment.getId());
            
            // Step 3: Create outbox entry (GUARANTEED publishing)
            TopUpCompletedEvent event = TopUpCompletedEvent.builder()
                .topupId(savedPayment.getId())
                .userId(userId)
                .amount(request.getAmount())
                .timestamp(Instant.now())
                .build();
            
            Outbox outboxEntry = Outbox.builder()
                .topic("topup-completed-topic")
                .payload(objectMapper.writeValueAsString(event))
                .aggregateId(savedPayment.getId())
                .processed(false)
                .build();
            
            outboxRepository.save(outboxEntry);
            log.info("Outbox entry created, ready for async publishing");
            
            // Everything committed in same transaction
            return new PaymentResponse(savedPayment);
            
        } catch (Exception e) {
            log.error("Payment creation failed for userId={}", userId, e);
            throw new PaymentProcessingException("Payment processing failed", e);
        }
        // LOCAL TRANSACTION END & COMMIT
    }
}

// ========== WALLET SERVICE (Step 2) ==========
@Service
@RequiredArgsConstructor
public class WalletService {
    private final WalletRepository walletRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = "topup-completed-topic",
        groupId = "wallet-group-v1",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleTopUpCompleted(String messageJson) {
        log.info("WalletService: Received TopUpCompleted event");
        
        try {
            // Step 1: Deserialize event
            TopUpCompletedEvent event = objectMapper.readValue(
                messageJson, TopUpCompletedEvent.class);
            
            log.info("TopUpCompleted event parsed: topupId={}, userId={}, amount={}", 
                     event.getTopupId(), event.getUserId(), event.getAmount());
            
            // Step 2: LOCAL TRANSACTION START
            // Validate idempotency (prevent duplicate processing)
            if (transactionLogRepository.existsByTopupId(event.getTopupId())) {
                log.warn("Duplicate TopUp already processed: topupId={}", 
                         event.getTopupId());
                return; // Idempotent - skip duplicate
            }
            
            // Step 3: Get wallet (lock for update to prevent race conditions)
            Wallet wallet = walletRepository.findByUserIdForUpdate(event.getUserId());
            if (wallet == null) {
                log.error("Wallet not found for userId={}", event.getUserId());
                publishWalletFailedEvent(event, "WALLET_NOT_FOUND");
                return;
            }
            
            // Step 4: Validate business rules
            BigDecimal newBalance = wallet.getBalance().add(event.getAmount());
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.error("Invalid balance calculation: wallet_id={}, new_balance={}", 
                          wallet.getId(), newBalance);
                publishWalletFailedEvent(event, "INVALID_BALANCE");
                return;
            }
            
            // Step 5: Update wallet balance
            wallet.setBalance(newBalance);
            wallet.setLastUpdated(Instant.now());
            walletRepository.save(wallet);
            log.info("Wallet updated: walletId={}, newBalance={}", 
                     wallet.getId(), newBalance);
            
            // Step 6: Log transaction
            TransactionLog log = TransactionLog.builder()
                .walletId(wallet.getId())
                .topupId(event.getTopupId())
                .amount(event.getAmount())
                .type(TransactionType.TOP_UP)
                .status(TransactionStatus.COMPLETED)
                .processedAt(Instant.now())
                .build();
            transactionLogRepository.save(log);
            
            // Step 7: Publish WalletUpdated event
            WalletUpdatedEvent walletUpdatedEvent = WalletUpdatedEvent.builder()
                .walletId(wallet.getId())
                .userId(event.getUserId())
                .newBalance(newBalance)
                .reason("TOP_UP_" + event.getTopupId())
                .timestamp(Instant.now())
                .build();
            
            Outbox outboxEntry = Outbox.builder()
                .topic("wallet-updated-topic")
                .payload(objectMapper.writeValueAsString(walletUpdatedEvent))
                .aggregateId(wallet.getId())
                .processed(false)
                .build();
            outboxRepository.save(outboxEntry);
            
            log.info("Wallet saga step completed successfully, " + 
                     "wallet-updated event queued for publishing");
            // LOCAL TRANSACTION END & COMMIT
            
        } catch (JsonException e) {
            log.error("Failed to parse TopUpCompleted event", e);
            // Don't publish failed event (input was invalid)
            throw new RuntimeException("Message parsing failed", e);
        } catch (Exception e) {
            log.error("Unexpected error processing TopUpCompleted event", e);
            throw new RuntimeException("Unexpected error in saga step", e);
        }
    }
    
    private void publishWalletFailedEvent(TopUpCompletedEvent trigger, String reason) {
        // Notify other services of failure
        WalletFailedEvent failEvent = WalletFailedEvent.builder()
            .reason(reason)
            .userId(trigger.getUserId())
            .topupId(trigger.getTopupId())
            .timestamp(Instant.now())
            .build();
        
        Outbox outbox = new Outbox("wallet-failed-topic", 
                                   objectMapper.writeValueAsString(failEvent));
        outboxRepository.save(outbox);
        log.warn("Wallet failure event published: reason={}", reason);
    }
}

// ========== INVOICE SERVICE (Step 3) ==========
@Service
@RequiredArgsConstructor
public class InvoiceService {
    
    @KafkaListener(
        topics = "wallet-updated-topic",
        groupId = "invoice-group-v1"
    )
    @Transactional
    public void handleWalletUpdated(String messageJson) {
        log.info("InvoiceService: Received WalletUpdated event");
        
        try {
            WalletUpdatedEvent event = objectMapper.readValue(
                messageJson, WalletUpdatedEvent.class);
            
            log.info("Creating invoice for wallet update: walletId={}, newBalance={}", 
                     event.getWalletId(), event.getNewBalance());
            
            // Check if already processed (idempotency)
            if (invoiceRepository.existsByWalletTransactionId(
                event.getWalletId())) {
                log.warn("Invoice already created for transaction");
                return;
            }
            
            // Create invoice
            Invoice invoice = Invoice.builder()
                .userId(event.getUserId())
                .walletTransactionId(event.getWalletId())
                .amount(event.getNewBalance())
                .reason(event.getReason())
                .status(InvoiceStatus.READY_DELIVERY)
                .createdAt(Instant.now())
                .build();
            
            invoiceRepository.save(invoice);
            log.info("Invoice created successfully: invoiceId={}", invoice.getId());
            
            // Optionally publish InvoiceCreatedEvent for further processing
            // (e.g., send email notification)
            
        } catch (Exception e) {
            log.error("Failed to create invoice from wallet update", e);
            // Soft fail - don't re-throw (invoice is non-critical)
            // Payment already succeeded, user has balance
        }
    }
}
```

---

## Implementation 2: Orchestration-Based Saga (Future)

### When to Use Orchestration

```
Choreography (Current - Good for):
✅ Simple flows (< 5 steps)
✅ Loose coupling important
✅ Each service owns business logic
❌ Hard to track overall flow
❌ Complex rollback logic difficult
❌ Circular dependencies possible

Orchestration (Future - Better for):
✅ Complex flows (5+ steps)
✅ Central control & visibility
✅ Easy rollback/compensation
✅ Easier to handle timeouts
❌ Requires central service
❌ Higher latency (more hops)
❌ Single point of coordination
```

### Orchestration Example (Temporary Pseudo-code)

```java
// Future Enhancement: Orchestration-based Saga

@Service
public class SagaOrchestrator {
    // Central coordinator for complex payment flows
    
    @Transactional
    public void orchestratePaymentWithRefund(PaymentRequest request) {
        SagaInstance saga = new SagaInstance("payment-with-refund");
        
        // Step 1
        Step1Result step1 = paymentService.createPayment(request);
        if (step1.isSuccess()) {
            saga.addStep("payment_created");
        } else {
            saga.compensate(); // Rollback all steps
            return;
        }
        
        // Step 2
        Step2Result step2 = walletService.creditWallet(step1.getPaymentId());
        if (step2.isSuccess()) {
            saga.addStep("wallet_updated");
        } else {
            saga.compensate(); // Rollback step 1
            return;
        }
        
        // Step 3
        Step3Result step3 = invoiceService.generateInvoice(step2.getWalletId());
        if (step3.isSuccess()) {
            saga.markComplete();
        } else {
            saga.compensate(); // Rollback all steps
        }
    }
}
```

---

## Failure Handling & Compensation

### Type 1: Service Unavailable (Transient Failure)

```
Scenario: Wallet Service is down when TopUpCompleted event is published

Timeline:
T0:     Payment service publishes -> Outbox
T0+1s:  OutboxPublisher tries Kafka send -> Success ✓
T0+1s:  Wallet listener - NOT RUNNING ✗
T0+2s:  Wallet comes back online
T0+3s:  Consumer group rebalances
T0+4s:  Wallet processes TopUpCompleted from Kafka offset
        └─ Balance updates ✓

Result: ✅ Saga completes successfully, just delayed

Recovery Mechanism:
1. Consumer offset tracking (committed offset = last processed)
2. Replayable events in Kafka
3. Idempotent consumers (no negative impact from reprocessing)
```

### Type 2: Business Logic Failure

```
Scenario: Wallet Service detects invalid amount during TopUp

Timeline:
T0:     Payment service: amount = -50 (invalid!) ❌
T0+1s:  Outbox publishes TopUpCompletedEvent
T0+1s:  Wallet listens, validates amount
        └─ VALIDATION FAILS ✗
        └─ Publishes wallet-failed-topic
T0+2s:  Identity Service listens to wallet-failed-topic
        └─ Marks user account for review
        └─ Sends security alert to user

Result: ✅ Failure detected and handled
```

### Type 3: Duplicate Message Processing

```
Problem: Service crashes between processing event and committing offset

Before:
Consumer processes message
├─ Updates DB ✓
├─ Tries to commit offset ✓
└─ Crashes before commit completes ✗

After restart:
├─ Consumer group rebalances
├─ Last committed offset unchanged
└─ Same message processed again! (duplicate)

Solution: Idempotent Consumers

@KafkaListener
public void handle(Event event) {
    // Check if already processed
    if (cache.contains(event.getId())) {
        log.info("Duplicate, skipping");
        return;
    }
    
    // Process
    process(event);
    
    // Mark as processed
    cache.add(event.getId());
}
```

---

## Monitoring Saga Execution

### Key Metrics to Track

```
For each Saga instance:
┌────────────────────────────────┐
│ SAGA EXECUTION METRICS          │
├────────────────────────────────┤
│ • Saga Start Time              │
│ • Steps Completed: 1/3, 2/3... │
│ • Step Duration: 100ms, 450ms  │
│ • Saga Status: PENDING, OK, ❌ 
│ • Compensation? Yes/No         │
└────────────────────────────────┘

Dashboard Queries:
- Median saga duration: 1.5 seconds
- Failed saga rate: 0.01% (1 in 10,000)
- Top failure reasons: Timeout, Invalid Amount
```

### Observability Code

```java
@Service
@RequiredArgsConstructor
public class SagaObserver {
    private final MeterRegistry meterRegistry;
    
    @KafkaListener(topics = "topup-completed-topic")
    public void handle(TopUpCompletedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Process
            walletService.processTopUp(event);
            
            sample.stop(Timer.builder("saga.step.wallet.update")
                .tag("status", "success")
                .register(meterRegistry));
                
        } catch (Exception e) {
            sample.stop(Timer.builder("saga.step.wallet.update")
                .tag("status", "failure")
                .register(meterRegistry));
        }
    }
}
```

---

## Best Practices

### 1. Saga Step Design

```java
// ❌ BAD: Too much in one transaction
public void procesPayment(PaymentRequest request) {
    validateCard();
    deductFromWallet();
    updateInventory();
    chargeTax();
    createInvoice();
    sendEmail();
    updateStats();
    // 7 things that could fail!
}

// ✅ GOOD: Single responsibility per step
public void step1_CreatePayment() {
    validateCard();
    savePayment();
}
// -> Publish payment-created event

public void step2_CreditWallet() {
    // Listen to payment-created
    deductFromWallet();
}
// -> Publish wallet-debited event

public void step3_CreateInvoice() {
    // Listen to wallet-debited
    createInvoice();
}
```

### 2. Event Versioning

```java
// Support multiple event versions for backward compatibility

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "version")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TopUpCompletedEventV1.class, name = "1"),
    @JsonSubTypes.Type(value = TopUpCompletedEventV2.class, name = "2")
})
public interface TopUpCompletedEvent {
    int getVersion();
}

public class TopUpCompletedEventV1 implements TopUpCompletedEvent {
    // Original fields
    public int getTopupId() { ... }
    public Long getUserId() { ... }
    
    public int getVersion() { return 1; }
}

public class TopUpCompletedEventV2 implements TopUpCompletedEvent {
    // Added new field
    public int getTopupId() { ... }
    public Long getUserId() { ... }
    public String getBillingAddress() { ... } // New
    
    public int getVersion() { return 2; }
}
```

### 3. Timeouts & Deadlines

```java
// Saga with timeout
@Service
public class PaymentSagaWithTimeout{
    
    @Transactional
    public void paymentWithTimeout(PaymentRequest request) {
        Payment payment = createPayment(request);
        
        // Set timeout for saga completion
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> {
            if (!paymentComplete.test(payment.getId())) {
                // Timeout reached - compensation
                compensatePayment(payment.getId());
                log.warn("Payment saga timed out: {}", payment.getId());
            }
        }, 5, TimeUnit.SECONDS); // 5 second timeout
    }
}
```

