# StockArena

## Main architecture
![architecture](./assets/stockarena.png)

## Detailed architecture
![detailed-architecture](./assets/stockarena_detail.png)

## Tech Stack
- Backend: Java 17+, Spring Boot (Web, WebSocket, Data JPA, Actuator)
- Messaging & Cache: Redis (Pub/Sub, caching)
- Database: PostgreSQL (transactional persistence, order/trade storage)
- Frontend: Vue 3 + Vite for real-time dashboards
- Containerization & Deployment: Docker, docker-compose
- Build Tooling: Maven

## Features

- **Designed and implemented a custom order matching engine** with **dedicated thread pools** and dispatch logic, achieving a balance between parallel processing and resource allocation. Ensured strict order sequencing, low latency, and high throughput under high-concurrency scenarios. Supported a **multi-consumer model** with configurable multiple matching engine instances for load distribution.
- Leveraged **Redis** to store high-frequency data such as candlesticks (K-line), user balances, and positions, effectively **reducing database load**. Built bi-directional **WebSocket** channels combined with **Redis Pub/Sub** to enable real-time subscription and push of candlestick data, ensuring low-latency connectivity and synchronization for a large number of clients.
- Integrated **Kafka MQ** to **asynchronously distribute** orders and matching results to matching engines, persistence services, and other downstream consumers, ensuring system decoupling and durability. Implemented an **idempotent** persistence service, while the matching engine applied **LRU caching and Bloom filters** to efficiently handle order deduplication.

## Modules
- API Layer (REST):
Provides endpoints for account management, order submission, queries, and trade history.
- WebSocket Service:
Real-time streaming of candlestick (K-line) data, order book depth, and trade events.
- Matching Engine:
  - Custom thread pool for efficient resource allocation
  - OrderBook implementation with price/time priority matching
  - Supports limit/market orders and extensible strategies
  - Multi-symbol concurrency with strict sequential processing per symbol
  - Caching Layer:
  Redis used for high-frequency data such as order book snapshots, top-N depth, and recent trades.
- Database Layer (Postgres):
Persistent storage of orders, trades, and account updates, optimized for batch writes.
- Deployment:
Docker Compose setup with app + Postgres + Redis + monitoring stack. Ready for CI pipelines (GitHub Actions / Jenkins).