package com.scm.domains.orders.repositories;

import com.scm.domains.dashboard.dtos.OrderMetrics;
import com.scm.domains.orders.entities.Order;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderDashboardRepository extends JpaRepository<Order, UUID> {

    /**
     * Aggregates total counts and values using database-level math calculations.
     * Maps the constructor signature directly into our dashboard DTO.
     */
    @Query("""
                SELECT new com.scm.domains.dashboard.dtos.OrderMetrics(
                    CAST(COUNT(o.id) AS int),
                    COALESCE(SUM(o.totalAmount), CAST(0.0 AS bigdecimal)),
                    CAST(SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END) AS int)
                )
                FROM Order o
                WHERE o.createdAt >= :since
            """)
    OrderMetrics getMetricsSince(@Param("since") OffsetDateTime since);

    /**
     * ANTI-N+1 PROTECTION: Uses JOIN FETCH to pull order data along with
     * its nested collections in a single database trip.
     */
    @QueryHints(@QueryHint(
            name = "hibernate.query.passDistinctThrough",
            value = "false"
    ))
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.createdAt >= :since ORDER BY o.createdAt DESC")
    List<Order> findRecentOrdersWithItems(@Param("since") OffsetDateTime since);
}