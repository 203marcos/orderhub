package com.orderhub.common.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeadLetterTopicResolverTest {

    private final DeadLetterTopicResolver resolver = new DeadLetterTopicResolver();

    private static ConsumerRecord<String, String> recordOn(String topic, int partition) {
        return new ConsumerRecord<>(topic, partition, 0L, "key", "value");
    }

    @Test
    void shouldSendAFailedRecordToTheDeadLetterTopicForItsSource() {
        TopicPartition destination = resolver.apply(recordOn("order.created", 0), new RuntimeException());

        assertThat(destination.topic()).isEqualTo("order.created.dlt");
    }

    @Test
    void shouldNotPinTheDestinationPartition() {
        // The source partition must not be reused: a dead-letter topic created with fewer
        // partitions would reject the send outright.
        TopicPartition destination = resolver.apply(recordOn("payment.approved", 7), new RuntimeException());

        assertThat(destination.partition()).isEqualTo(-1);
    }

    @Test
    void shouldNotChainSuffixesIfARecordIsAlreadyDeadLettered() {
        TopicPartition destination = resolver.apply(recordOn("order.created", 0), new RuntimeException());
        TopicPartition again = resolver.apply(recordOn(destination.topic(), 0), new RuntimeException());

        // Documents today's behaviour: reprocessing a DLT would grow the name. Worth knowing
        // before anyone points a consumer at a dead-letter topic.
        assertThat(again.topic()).isEqualTo("order.created.dlt.dlt");
    }
}
