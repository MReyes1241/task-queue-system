
# Distributed Task Queue System

> A distributed asynchronous task processing system built with Java Spring Boot and Redis. It features task prioritization, retry mechanisms, auto-scaling workers, and a RESTful API for job management. The system handles background processing such as image resizing, email notifications, and data exports, with Docker deployment on AWS.

## Project Overview

This project is a deep-dive learning experience in building distributed systems from first principles. Rather than relying on frameworks like Celery or AWS SQS, this system was developed to explore the architectural patterns, trade-offs, and challenges of distributed computing at scale.

The system accepts jobs via REST API, distributes them across multiple worker nodes, handles failures gracefully, and provides monitoring and persistence—maintaining high availability and eventual consistency.

### Real-World Use Cases
- Image processing pipelines (resize, compress, upload to S3)
- Email notification systems (transactional and bulk)
- Data export generation (large CSV or Excel files)
- Third-party API synchronization (Stripe, Salesforce, etc.)

---

## Architecture

```

API Server (Spring Boot)
↓
Redis Cluster (Message Broker)
↓
Worker Pool (Auto-scaling)
↓
PostgreSQL (Persistent Storage)
↓
Monitoring (Prometheus + Grafana)

```

### Key Design Decisions

**Why Redis over RabbitMQ**
- Simpler learning curve while still production-ready
- Dual-purpose: message broker and cache
- Excellent performance and reliability
- Rich data structures (Lists, Hashes, Sorted Sets)

**CAP Theorem Choice: AP (Availability + Partition Tolerance)**
- System remains operational during network failures
- Eventual consistency over perfect consistency
- Idempotent job design prevents duplication issues

**Inspired by Sidekiq’s Architecture**
- Redis-backed simplicity
- Proven scalability
- Clear separation of concerns and extensibility

---

## Core Features

### Implemented
- Job submission via REST API
- Priority queue support (High, Medium, Low)
- Automatic retry with exponential backoff
- Dead letter queue for failed jobs
- Visibility timeout to prevent duplicate processing
- Worker health monitoring
- Job status tracking
- PostgreSQL persistence

### In Progress
- Scheduled jobs (delayed execution)
- Auto-scaling based on queue depth
- Comprehensive metrics and monitoring
- Docker Compose setup

### Planned
- AWS deployment (ECS, ElastiCache, RDS)
- CloudWatch integration
- Admin dashboard
- Job dependencies
- Rate limiting

---

## Technology Stack

**Backend:**
- Java 17
- Spring Boot 3.x
- Spring Data Redis
- Spring Data JPA
- Spring Boot Actuator

**Infrastructure:**
- Redis 7.x
- PostgreSQL 15
- Docker & Docker Compose
- AWS (ECS, ElastiCache, RDS)

**Monitoring:**
- Prometheus
- Grafana
- Spring Boot Actuator

---

## Learning Journey

This project is structured as a multi-week exploration of distributed systems and backend design.

### Week 1: Distributed Systems Fundamentals (Completed)
- Studied CAP theorem, consistency models, and message queue patterns
- Analyzed Celery, Sidekiq, AWS SQS, and Kafka architectures
- Designed the system architecture and data models

**Key Insights:**
- Visibility timeout prevents duplicate processing
- Dead letter queues isolate poison messages
- Exponential backoff allows graceful recovery

### Week 2: Redis and Basic Implementation (In Progress)
- Implementing Redis queue operations (RPUSH, LPOP, BRPOPLPUSH)
- Building the visibility timeout mechanism
- Developing the Spring Boot API and worker implementation
- Designing PostgreSQL schema for job persistence

### Weeks 3–8: Reliability and Production Features (Planned)
- Retry logic, worker heartbeats, monitoring, and containerization
- Integration and performance testing

### Weeks 9–16: AWS Deployment and Scaling (Planned)
- Auto-scaling worker infrastructure
- CloudWatch integration
- Admin dashboard and metrics visualization

---

## Skills Demonstrated

**Distributed Systems:** CAP theorem, failure handling, eventual consistency  
**Backend Engineering:** REST APIs, data modeling, async job execution  
**System Design:** Scalability, observability, and monitoring  
**DevOps:** Docker, AWS deployment, and CI/CD fundamentals

---

## Architecture Details

### Job State Machine
```

PENDING → PROCESSING → COMPLETED
↓           ↓            ↑
└─────→ FAILED ──────────┘
↓
DEAD (max retries exceeded)

```

### Redis Data Structures
- Lists → Queues (main, priority, processing, DLQ)
- Hashes → Job metadata and worker heartbeats
- Sorted Sets → Scheduled and retry queues

### Failure Handling Strategy
1. Worker crash → Visibility timeout re-queues job
2. Transient failure → Retry with exponential backoff
3. Permanent failure → Move to dead letter queue
4. Redis crash → Recovery via AOF and PostgreSQL
5. Database downtime → Redis cache reconciliation

---

## Monitoring and Metrics

**Queue Metrics:** Depth, processing rate, wait time  
**Worker Metrics:** Utilization, throughput, health  
**Job Metrics:** Success/failure rate, latency, retry counts

---

## Current Status

**Phase:** Week 2 – Redis Fundamentals and Basic Implementation  
**Next Steps:**
1. Complete Redis queue operations
2. Implement core Spring Boot worker
3. Establish full job lifecycle
4. Add retries and DLQ handling

---

## About

This project is part of my ongoing effort to understand distributed system architecture beyond abstraction. It is continuously evolving, and each stage reflects deeper insights into scalability, reliability, and system resilience.

**Last Updated:** Week 2 – Redis Implementation Phase
