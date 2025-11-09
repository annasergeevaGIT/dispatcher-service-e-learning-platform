# Dispatcher Service

The Dispatcher Service is responsible for processing student enrollment events within the E-Learning Platform.
It listens to new enrollment events published to Kafka by the Enrollment Service, decides whether the enrollment should be approved or rejected, and then publishes the result back to Kafka for other services to consume.

## Related Services

| Service                                                               | Description                       |
|-----------------------------------------------------------------------|-----------------------------------|
| [Course Service](https://github.com/annasergeevaGIT/course-service)   | Handles courses                   |
| [Feedback Service](https://github.com/annasergeevaGIT/feedback-service) | Manages user feedback             |
| [Course Aggregate Service](https://github.com/annasergeevaGIT/course-aggregate-service)               | Aggregates course and review data |
| [Gateway Service](../gateway-service)                                 | Routes requests to microservices  |
| [Config Server](https://github.com/annasergeevaGIT/config-server-e-learning-platform)                                     | Centralized configuration storage |

## Overview

The Dispatcher Service processes events about newly created enrollments that are received from Kafka and decides whether each enrollment should be approved or rejected.
The logic in this educational version is intentionally minimal:
- Enrollments with even IDs are approved.
- Enrollments with odd IDs are rejected.
After processing, the service publishes an EnrollmentDispatchedEvent to a Kafka topic, notifying other services (like Enrollment Service) about the updated enrollment status.

## Kafka Integration Flow

- Enrollment Service saves the enrollment data and writes an entry into the enrollments_outbox table (following the Outbox pattern).
- Kafka Connect with the Debezium Postgres Connector monitors this table and publishes new events to the Kafka topic v1.public.enrollments_outbox.
- Dispatcher Service consumes each event (EnrollmentPlacedEvent) from this topic, deserializes it using Confluent Schema Registry, processes the enrollment, and generates a new event EnrollmentDispatchedEvent.
- The Dispatcher Service Producer sends this dispatch event to another Kafka topic, v1.enrollments_dispatch.
- Enrollment Service consumes messages from v1.enrollments_dispatch and updates the enrollment status in PostgreSQL.

## Kafka & Avro Serialization
The Dispatcher Service uses Apache Avro for efficient message serialization and deserialization.
- The Confluent Schema Registry stores Avro schemas for all event types.
- When producing a message, the Kafka Producer automatically registers its Avro schema in the Schema Registry (if not already registered).
- When consuming a message, the Kafka Consumer retrieves the schema from the registry and caches it locally for future messages.
This ensures schema compatibility and version management across services.

## Kafka Topics
- v1.public.enrollments_outbox - Outgoing enrollment creation events, published by Debezium
- v1.enrollments_dispatch - Processed enrollment events, published by the Dispatcher Service

## Endpoints

| Method | Endpoint | Description                             |
|---------|-----------|-----------------------------------------|
| `GET` | `/actuator/health` | Returns the current service health status                    |
| `GET` | `/actuator/prometheus` | Exposes Prometheus-compatible metrics                    |

## Tech Stack

- **Java 21**
- **Spring Boot 3**
- **Spring Kafka**
- **Apache Kafka**
- **Confluent Schema Registry**
- **Debezium Postgres Connector**
- **PostgreSQL (used by Enrollment Service)**
- **Docker**
- **Gradle**
- **Micrometer / Prometheus (for metrics and monitoring)**

## Build & Run

```bash
./gradlew clean bootBuildImage
docker-compose up -d
