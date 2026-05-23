package com.example.flexhub.transaction;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

// The auto-configured KafkaTemplate<String, Object> uses JsonSerializer — it serializes
// Java objects to JSON on send. The outbox stores ALREADY-serialized JSON strings, so we
// need a second producer that uses StringSerializer (pass-through, no re-serialization).
// Same broker, same connection pool — just a different value serializer.
@Configuration
class KafkaConfig {

    @Bean
    ProducerFactory<String, String> stringProducerFactory(
            KafkaProperties properties,
            ObjectProvider<KafkaConnectionDetails> connectionDetails) {
        Map<String, Object> props = properties.buildProducerProperties(null);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // @ServiceConnection (Testcontainers) populates KafkaConnectionDetails with the
        // dynamic container bootstrap; KafkaProperties still reflects application.yml.
        // Spring's auto-configured ProducerFactory overrides bootstrap from connection
        // details — mirror that here so our string producer follows the same path.
        KafkaConnectionDetails details = connectionDetails.getIfAvailable();
        if (details != null) {
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, details.getBootstrapServers());
        }
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    KafkaTemplate<String, String> stringKafkaTemplate(ProducerFactory<String, String> stringProducerFactory) {
        return new KafkaTemplate<>(stringProducerFactory);
    }
}
