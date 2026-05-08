package com.scm.domains.orders.repositories;


import com.scm.domains.orders.entities.OrderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {

    /**
     * Finds events that haven't been processed yet.
     * Ordered by creation date to ensure sequential processing.
     */
    @Query("SELECT o FROM OrderOutbox o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OrderOutbox> findPendingEvents();


    List<OrderOutbox> findTop10ByStatusOrderByCreatedAtAsc(String status);

}