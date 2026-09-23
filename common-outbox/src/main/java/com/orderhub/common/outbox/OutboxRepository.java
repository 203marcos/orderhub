package com.orderhub.common.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims the next batch of unpublished events for this instance.
     *
     * <p>The pessimistic write lock plus {@code SKIP LOCKED} (Hibernate's lock timeout of -2)
     * is what makes the relay safe to run on more than one replica: a row already claimed by
     * another poller is stepped over instead of blocking or being published twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Limit limit);
}
