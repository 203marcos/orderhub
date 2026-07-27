package com.orderhub.common.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

import java.util.function.BiFunction;

/**
 * Decides where a record goes once it has failed every retry: {@code <source topic>.dlt}.
 *
 * <p>A named class rather than a lambda buried in the error handler, because the naming rule
 * is an operational contract — dashboards, alerts and anyone draining a dead-letter topic all
 * depend on it — and it deserves a test that fails if someone changes it by accident.
 */
public class DeadLetterTopicResolver implements BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> {

    static final String SUFFIX = ".dlt";

    /** Let the partitioner choose: the dead-letter topic may have fewer partitions than the source. */
    private static final int ANY_PARTITION = -1;

    @Override
    public TopicPartition apply(ConsumerRecord<?, ?> record, Exception exception) {
        return new TopicPartition(record.topic() + SUFFIX, ANY_PARTITION);
    }
}
