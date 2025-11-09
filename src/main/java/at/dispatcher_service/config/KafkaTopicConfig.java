package at.dispatcher_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${kafkaprops.enrollment-dispatch-topic}")
    private String enrollmentsDispatchTopic;
    @Value("${kafkaprops.replication-factor}")
    private Integer replicationFactor;
    @Value("${kafkaprops.partitions-count}")
    private Integer partitionsCount;

    @Bean
    public NewTopic enrollmentsDispatchTopic() {
        return TopicBuilder
                .name(enrollmentsDispatchTopic)
                .replicas(replicationFactor)
                .partitions(partitionsCount)
                .build();
    }
}
