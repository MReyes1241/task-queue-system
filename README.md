# Distributed Task Queue System

> A distributed task processing system built with Java Spring Boot, PostgreSQL, and Redis. Features exponential backoff retry logic, dead letter queue, structured JSON logging, and Docker containerization—demonstrating enterprise backend patterns for production systems.

## Project Overview

This project began as a deep-dive learning experience in building distributed systems from first principles. Rather than relying on frameworks like Celery or AWS SQS, I built this system to understand the architectural patterns, trade-offs, and challenges of distributed computing at scale.

The system accepts jobs via REST API, persists them to PostgreSQL, distributes them via Redis queues, handles failures gracefully with sophisticated retry logic, and provides comprehensive monitoring—all while maintaining production-ready code quality.

### Real-World Use Cases
- **Image processing pipelines**: Resize, compress, upload to S3
- **Email notification systems**: Transactional and bulk sending
- **Data export generation**: Large CSV or Excel files
- **Third-party API synchronization**: Stripe, Salesforce, payment gateways
- **Background ETL jobs**: Data transformation and loading

---

## Architecture
```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Client    │────▶│  Spring Boot │────▶│ PostgreSQL  │
│   (REST)    │     │  Application │     │  (Jobs DB)  │
└─────────────┘     └──────┬───────┘     └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │    Redis    │
                    │ (Job Queue) │
                    └─────────────┘
```

### Key Design Decisions

**PostgreSQL + Redis (Hybrid Approach)**
- PostgreSQL for durable job storage and audit trail
- Redis for fast job distribution and queueing
- Best of both worlds: durability + speed
- Jobs survive system restarts

**Retry Logic: Exponential Backoff with Jitter**
- Prevents thundering herd problem
- Formula: `delay = baseDelay * 2^retryCount + jitter`
- Progression: 2s → 4s → 8s → Dead Letter Queue
- Jitter prevents synchronized retries

**Inspired by Production Systems**
- **Sidekiq**: Redis simplicity, retry patterns
- **AWS SQS**: Dead letter queue, visibility timeout concepts
- **IBM Patterns**: Structured logging, health checks, containerization

**CAP Theorem Choice: CP (Consistency + Partition Tolerance)**
- PostgreSQL ensures consistency (ACID transactions)
- Redis provides partition tolerance
- Availability maintained through health checks and container orchestration

---

## Core Features

### Implemented (Production-Ready)

**Job Management**
- REST API for job submission, status, and history
- Pagination and filtering (by status, type)
- Manual retry and cancellation
- System-wide statistics endpoint

**Reliability**
- Exponential backoff retry (2s → 4s → 8s)
- Dead letter queue for failed jobs
- Complete audit trail (job_history table)
- Transactional job persistence

**Observability**
- Structured JSON logging (Logback + Logstash)
- MDC context tracking (job_id, job_type, retry_count)
- Spring Boot Actuator health checks
- Database and Redis connection monitoring

**DevOps**
- Multi-stage Docker build (~200MB final image)
- Docker Compose orchestration
- Environment-based configuration
- Non-root container security
- Health checks for Kubernetes compatibility

### In Progress
- Priority queue support
- Scheduled jobs (cron-like execution)
- Auto-scaling based on queue depth

### Planned
- Prometheus metrics integration
- Grafana dashboards
- Kubernetes deployment manifests
- CI/CD pipeline (GitHub Actions)
- AWS deployment (ECS, ElastiCache, RDS)

#### Weeks 3–8: Reliability and Production Features (Planned)
- Retry logic, worker heartbeats, monitoring, and containerization
- Integration and performance testing

#### Weeks 9–16: AWS Deployment and Scaling (Planned)
- Auto-scaling worker infrastructure
- CloudWatch integration
- Admin dashboard and metrics visualization
---

## Technology Stack

**Backend**
- Java 21 (LTS with modern features)
- Spring Boot 4.0.3
- Spring Data JPA (Hibernate)
- Spring Data Redis
- Spring Boot Actuator

**Data Layer**
- PostgreSQL 15 (job persistence + audit trail)
- Redis 7 (job queue + caching)
- HikariCP (connection pooling)

**DevOps**
- Docker (multi-stage builds)
- Docker Compose (local orchestration)
- Logback + Logstash (structured logging)

**Future**
- Prometheus + Grafana (metrics)
- Kubernetes (orchestration)
- AWS (cloud deployment)

---

## Quick Start

### Using Docker Compose (Recommended)
```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/task-queue-system.git
cd task-queue-system/task-queue-core

# Start entire stack (PostgreSQL + Redis + App)
docker compose up

# Application available at http://localhost:8080
```

### Local Development
```bash
# Prerequisites: PostgreSQL and Redis running locally
createdb taskqueue

# Build and run
./gradlew bootRun

# Application starts on http://localhost:8080
```

---

## API Examples

### Submit a Job
```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"type":"send_email","data":"user@example.com"}'

# Response:
# {"jobId":"abc-123","status":"submitted"}
```

### Check Job Status
```bash
curl http://localhost:8080/api/jobs/abc-123

# Response includes: status, timestamps, retry count, error messages
```

### View Job History (Audit Trail)
```bash
curl http://localhost:8080/api/jobs/abc-123/history

# Returns complete lifecycle:
# [
#   {"status":"QUEUED","message":"Job submitted and queued"},
#   {"status":"PROCESSING","message":"Job started processing"},
#   {"status":"COMPLETED","message":"Job completed successfully"}
# ]
```

### List Failed Jobs
```bash
curl "http://localhost:8080/api/jobs?status=FAILED&page=0&size=20"
```

### System Statistics
```bash
curl http://localhost:8080/api/jobs/stats

# Response:
# {
#   "total": 1543,
#   "byStatus": {"COMPLETED": 1489, "FAILED": 15, "DEAD_LETTER": 24},
#   "byType": {"send_email": 834, "process_image": 456},
#   "queueDepth": 12
# }
```

### Health Check
```bash
curl http://localhost:8080/actuator/health

# Returns: PostgreSQL status, Redis status, disk space
```

---

## Database Schema

### Jobs Table
```sql
CREATE TABLE jobs (
    id VARCHAR(255) PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    data TEXT,
    status VARCHAR(50) NOT NULL,
    priority INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP,
    queued_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    next_retry_at TIMESTAMP,
    error_message TEXT
);
```

### Job History Table (Audit Trail)
```sql
CREATE TABLE job_history (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    message TEXT,
    created_at TIMESTAMP,
    FOREIGN KEY (job_id) REFERENCES jobs(id)
);

CREATE INDEX idx_job_history_job_id ON job_history(job_id);
```

---

## Retry Logic Deep Dive

### Exponential Backoff Formula
```
delay = baseDelay * (2 ^ retryCount) + jitter
where:
  baseDelay = 2 seconds
  jitter = random(0-1 seconds)
  maxDelay = 60 seconds (capped)
```

### Example Timeline
```
Job fails at 10:00:00

Retry 1: 10:00:02 (2s delay)  → Fails
Retry 2: 10:00:06 (4s delay)  → Fails  
Retry 3: 10:00:14 (8s delay)  → Fails
Final:   10:00:14 → Moved to DEAD_LETTER
```

### Why This Works
- **Spreads load over time**: Prevents overwhelming failing services
- **Jitter prevents thundering herd**: Random delays prevent synchronized retries
- **Dead letter queue**: Preserves failed jobs for investigation
- **Manual retry available**: POST /api/jobs/{id}/retry

---

## Structured Logging

### Development Mode (Human-Readable)
```
2026-02-24 21:12:35 INFO [main] JobConsumer : Processing job started
2026-02-24 21:12:37 INFO [main] JobConsumer : Job completed successfully
```

### Production Mode (JSON for Splunk/Elasticsearch)
```json
{
  "@timestamp": "2026-02-24T21:12:35.123Z",
  "level": "INFO",
  "logger": "JobConsumer",
  "message": "Processing job started",
  "job_id": "abc-123",
  "job_type": "send_email",
  "retry_count": "0",
  "application": "task-queue"
}
```

**Switch modes:**
```bash
# Development (default)
./gradlew bootRun

# Production
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

---

## Docker Commands
```bash
# Build and start all services
docker compose up --build

# Run in background
docker compose up -d

# View logs (all services)
docker compose logs -f

# View app logs only
docker compose logs -f app

# Stop all services
docker compose down

# Clean slate (removes data)
docker compose down -v

# Enter PostgreSQL
docker compose exec postgres psql -U taskqueue_user -d taskqueue

# Enter app container
docker compose exec app sh
```

---

## Learning Journey

This project evolved through a structured, multi-week learning process:

### Week 1: Distributed Systems Fundamentals (Completed)
- Studied CAP theorem, consistency models, message queue patterns
- Analyzed Celery, Sidekiq, AWS SQS, and Kafka architectures
- Read "Designing Data-Intensive Applications" (Martin Kleppmann)
- Designed system architecture and data models

**Key Insights:**
- Idempotent job design prevents duplication
- Dead letter queues isolate poison messages
- Two-table design (jobs + history) optimizes queries

### Week 2: Core Implementation (Completed)
- Built Redis queue operations (LPUSH, BRPOP)
- Implemented Spring Boot REST API
- Added PostgreSQL persistence with JPA/Hibernate
- Created job history audit trail
- Implemented retry logic with exponential backoff
- Added dead letter queue
- Built complete REST API with pagination

**Challenges Overcome:**
- Spring Boot auto-configuration conflicts
- ObjectMapper bean issues
- LocalDateTime serialization
- Database connection pooling tuning

### Week 2 (Days 12-13): Production Patterns (Completed)
- Added structured JSON logging (Logback + Logstash)
- Implemented Docker multi-stage builds
- Created Docker Compose orchestration
- Added Spring Boot Actuator health checks
- Environment-based configuration
- MDC context tracking

**IBM-Style Patterns Implemented:**
- Non-root container security
- Health checks for Kubernetes
- Structured logging for observability
- Multi-stage builds for smaller images

### Next: Scaling & Deployment (In Progress)
- Kubernetes deployment manifests
- Prometheus metrics
- Grafana dashboards
- CI/CD pipeline
- AWS deployment

---

## Skills Demonstrated

**Distributed Systems**
- CAP theorem trade-offs
- Retry logic and failure handling
- Eventual consistency patterns
- Message queue architecture

**Backend Engineering**
- RESTful API design
- Data modeling (PostgreSQL schema)
- Asynchronous job processing
- Transaction management

**Production Engineering (IBM Patterns)**
- Structured JSON logging
- Docker containerization
- Health check endpoints
- Environment-based configuration
- Connection pooling
- Non-root security

**System Design**
- Scalability considerations
- Observability and monitoring
- Error handling strategies
- Database indexing

---

## Project Structure
```
task-queue-core/
├── src/main/java/com/mreyes/task_queue/
│   ├── controller/         # REST API endpoints
│   ├── dto/                # Data transfer objects
│   ├── model/              # JPA entities
│   ├── repository/         # Database access layer
│   ├── scheduler/          # Retry scheduler
│   ├── service/            # Business logic
│   └── worker/             # Job processing worker
├── src/main/resources/
│   ├── application.properties
│   └── logback-spring.xml  # Logging configuration
├── Dockerfile              # Multi-stage build
├── docker-compose.yml      # Local orchestration
├── README.md               # This file
├── ARCHITECTURE.md         # Deep dive into design
└── build.gradle            # Dependencies
```

---

## Why This Project Matters

**For Learning:**
- Built from first principles, not just using a framework
- Explored real-world trade-offs in distributed systems
- Implemented production patterns used at IBM, Amazon, Google

**For Interviews:**
- Demonstrates deep understanding of backend architecture
- Shows ability to make informed design decisions
- Proves capability to build production-ready systems

**For Portfolio:**
- Complete, working system with Docker deployment
- Comprehensive documentation
- Clean, professional codebase

---

## Comparison to Production Systems

### vs Celery (Python)
- **Similar**: Redis-based, retry logic, result persistence  
- **Different**: Java's type safety, JPA for persistence  
- **Trade-off**: More verbose but catches errors at compile time

### vs Sidekiq (Ruby)
- **Similar**: Redis queue, exponential backoff, DLQ  
- **Different**: PostgreSQL persistence (Sidekiq uses Redis only)  
- **Trade-off**: More durable, slightly slower

### vs AWS SQS
- **Similar**: Retry logic, DLQ, visibility timeout concepts  
- **Different**: Self-hosted vs managed service  
- **Trade-off**: No vendor lock-in, lower cost at small scale

---

## Contributing

This is a learning project, but suggestions and feedback are welcome! Feel free to open issues or submit pull requests.

---

## License

MIT License - feel free to use this for learning or your own projects.

---

## Author

**Manuel Reyes Jr.**  

Built as part of a comprehensive backend engineering portfolio demonstrating production-ready distributed systems patterns.

**Connect:**
- GitHub: [[MReyes1241](https://github.com/MReyes1241)]
- LinkedIn: [[Manuel Reyes jr](https://www.linkedin.com/in/manuel-reyes-jr-swe/)]
- Email: [Manuelreyes1241@outlook.com]

---

**Last Updated:** Days 12-13 – Production Patterns (Structured Logging, Docker, Health Checks)
