package at.dispatcher_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import at.AvroEnrollmentPlacedEvent;
import at.EnrollmentDispatchStatus;
import at.EnrollmentDispatchedEvent;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
@Service
public class DispatcherService {

    private final KafkaTemplate<String, EnrollmentDispatchedEvent> kafkaTemplate;
    @Value("${kafkaprops.enrollment-dispatch-topic}")
    private String enrollmentDispatchTopic;
    @Value("${kafkaprops.nack-sleep-duration}")
    private Duration nackSleepDuration;

    @KafkaListener(topics = {"${kafkaprops.enrollment-placed-topic}"})
    public void consumeEnrollmentPlacedEvent(AvroEnrollmentPlacedEvent event,
                                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        Acknowledgment acknowledgment) {
        log.info("Consuming message from Kafka: {}. Key: {}. Partition: {}. Topic: {}",
                event, key, partition, topic);
        var enrollmentDispatchEvent = processEvent(event);
        try {
            kafkaTemplate.send(enrollmentDispatchTopic, key, enrollmentDispatchEvent).get();
            log.info("Successfully processed and sent EnrollmentDispatchedEvent {} to Kafka.",
                    enrollmentDispatchEvent);
            // commit the offset
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to send EnrollmentDispatchedEvent {} to Kafka", enrollmentDispatchEvent);
            // don't commit the offset
            acknowledgment.nack(nackSleepDuration);
        }
    }

    private EnrollmentDispatchedEvent processEvent(AvroEnrollmentPlacedEvent event) {
        log.info("Processing EnrollmentPlacedEvent: {}", event);
        EnrollmentDispatchStatus status = event.getEnrollmentId() % 2 == 0 ? EnrollmentDispatchStatus.ACCEPTED : EnrollmentDispatchStatus.REJECTED;
        return EnrollmentDispatchedEvent.newBuilder()
                .setEnrollmentId(event.getEnrollmentId())
                .setStatus(status)
                .build();
    }
}