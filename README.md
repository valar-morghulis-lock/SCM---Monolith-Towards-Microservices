SCM: Modular Monolith towards Microservices
A high-performance Supply Chain Management core built to demonstrate the evolution from a centralized monolith to an event-driven distributed system. This project leverages Java 21 and Spring Boot 4 to handle complex warehouse transactions with extreme efficiency.

🏗️ Core Architecture & Tech Stack
Runtime: Java 21 with Project Loom (Virtual Threads) to handle high-concurrency warehouse transactions without thread exhaustion.

Persistence: jOOQ for type-safe, fluent SQL and Pessimistic Locking to ensure data integrity during critical stock updates.

Consistency: Implementation of the Transactional Outbox Pattern to ensure "Exactly-Once" semantics between the database and Kafka.

Messaging: Apache Kafka for reliable, asynchronous communication between Order, Inventory, and future services.


🔄 Order Lifecycle (The Saga Flow)
The system currently manages the following distributed transaction flow.

Order Placement: OrderService saves order details and an ORDER_CREATED event in a single atomic transaction.

Event Relay: A SchedulingTask polls the outbox and dispatches events to Kafka, Debezium will replace the Scheduler later on.

Inventory Reservation: InventoryService consumes the event, reserves stock (e.g., DRILL-HD-001), and emits INVENTORY_RESERVED.

Order Validation: OrderService consumes the reservation success and promotes the order status to VALIDATED.

🚀 Roadmap: Next Steps
We are currently moving into the Payment and Fulfillment phases:

[ ] Payment Service Integration: Implementation of ORDER_VALIDATED consumption and fund processing.

[ ] Compensating Transactions: Logic to release inventory reservations automatically if a payment fails (The "Undo" logic).

[ ] Logistics Orchestration: Transitioning from digital reservation to physical packaging and shipping manifest generation.

🛠️ Getting Started
Bash
# Clone the repository
git clone https://github.com/your-repo/scm-microservices.git](https://github.com/valar-morghulis-lock/SCM---Monolith-Towards-Microservices.git

# Build and spin up the environment
./gradlew build
docker-compose up -d
