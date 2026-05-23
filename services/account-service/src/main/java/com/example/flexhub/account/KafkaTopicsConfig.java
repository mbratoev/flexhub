package com.example.flexhub.account;

import com.example.flexhub.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Both services declare both sides of the topic list — KafkaAdmin deduplicates by name,
// so declaring twice is a no-op. Each service is self-sufficient about the topics it touches.
@Configuration
class KafkaTopicsConfig {

    @Bean NewTopic transfersRequested()   { return topic(Topics.TRANSFERS_REQUESTED); }
    @Bean NewTopic accountsDebited()      { return topic(Topics.ACCOUNTS_DEBITED); }
    @Bean NewTopic accountsDebitRejected() { return topic(Topics.ACCOUNTS_DEBIT_REJECTED); }
    @Bean NewTopic accountsCredited()     { return topic(Topics.ACCOUNTS_CREDITED); }
    @Bean NewTopic accountsCreditFailed() { return topic(Topics.ACCOUNTS_CREDIT_FAILED); }
    @Bean NewTopic transfersReversed()    { return topic(Topics.TRANSFERS_REVERSED); }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }
}
