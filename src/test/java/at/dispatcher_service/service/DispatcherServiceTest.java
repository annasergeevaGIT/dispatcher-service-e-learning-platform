package at.dispatcher_service.service;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import at.AvroEnrollmentPlacedEvent;
import at.EnrollmentDispatchStatus;
import at.EnrollmentDispatchedEvent;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest
class DispatcherServiceTest {

    public static final String CONFLUENT_VERSION = "7.5.2";
    private static final String ENROLLMENT_DISPATCH_TOPIC = "v1.enrollments_dispatch";
    private static final String ENROLLMENT_PLACED_TOPIC = "v1.public.enrollments_outbox";

    private static final Network NETWORK = Network.newNetwork();

    public static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.2"))
            .withKraft()
            .withNetwork(NETWORK);

    public static final SchemaRegistryContainer SCHEMA_REGISTRY =
            new SchemaRegistryContainer(CONFLUENT_VERSION);

    @BeforeAll
    static void setup() {
        KAFKA.start();
        SCHEMA_REGISTRY.withKafka(KAFKA).start();

        System.setProperty("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
        System.setProperty("spring.kafka.consumer.properties.schema.registry.url", "http://localhost:" + SCHEMA_REGISTRY.getFirstMappedPort());
        System.setProperty("spring.kafka.producer.properties.schema.registry.url", "http://localhost:" + SCHEMA_REGISTRY.getFirstMappedPort());
    }


    @Autowired
    private KafkaTemplate<String, AvroEnrollmentPlacedEvent> kafkaTemplate;
    private Consumer<String, EnrollmentDispatchedEvent> outputConsumer;

    @BeforeEach
    void setupConsumer() {
        Map<String, Object> consumerProps = new HashMap<>(KafkaTestUtils.consumerProps(KAFKA.getBootstrapServers(), "testConsumer", "true"));
        consumerProps.put("key.deserializer", StringDeserializer.class);
        consumerProps.put("value.deserializer", KafkaAvroDeserializer.class);
        consumerProps.put("schema.registry.url", "http://localhost:" + SCHEMA_REGISTRY.getFirstMappedPort());
        consumerProps.put("specific.avro.reader", "true");

        outputConsumer = new DefaultKafkaConsumerFactory<String, EnrollmentDispatchedEvent>(consumerProps).createConsumer();
        outputConsumer.subscribe(Collections.singletonList(ENROLLMENT_DISPATCH_TOPIC));
        outputConsumer.poll(Duration.ofMillis(0));
    }

    @AfterEach
    void closeConsumer() {
        outputConsumer.close();
    }

    @Test
    void consumeEnrollmentPlacedEvent_rejectsEnrollmentWithOddId() {
        Long toBeRejected = 1L;
        var input = buildEvent(toBeRejected);

        kafkaTemplate.send(ENROLLMENT_PLACED_TOPIC, "1", input);

        ConsumerRecord<String, EnrollmentDispatchedEvent> consumed = KafkaTestUtils.getSingleRecord(outputConsumer, ENROLLMENT_DISPATCH_TOPIC, Duration.ofSeconds(10));

        assertThat(consumed.value().getEnrollmentId()).isEqualTo(toBeRejected);
        assertThat(consumed.value().getStatus()).isEqualTo(EnrollmentDispatchStatus.REJECTED);
    }

    @Test
    void consumeEnrollmentPlacedEvent_acceptsEnrollmentWithEvenId() {
        Long toBeAccepted = 2L;
        var input = buildEvent(toBeAccepted);

        kafkaTemplate.send(ENROLLMENT_PLACED_TOPIC, "2", input);

        ConsumerRecord<String, EnrollmentDispatchedEvent> consumed = KafkaTestUtils.getSingleRecord(outputConsumer, ENROLLMENT_DISPATCH_TOPIC, Duration.ofSeconds(10));

        assertThat(consumed.value().getEnrollmentId()).isEqualTo(toBeAccepted);
        assertThat(consumed.value().getStatus()).isEqualTo(EnrollmentDispatchStatus.ACCEPTED);
    }

    private AvroEnrollmentPlacedEvent buildEvent(Long eventId) {
        return AvroEnrollmentPlacedEvent.newBuilder()
                .setEnrollmentId(eventId)
                .setApartment(1)
                .setCity("Moscow")
                .setStreet("Street")
                .setHouse(1)
                .setCreatedBy("Alex")
                .setCreatedAt(System.currentTimeMillis())
                .setLsn$1(10L)
                .setSourceTsMs$1(100L)
                .build();
    }

}