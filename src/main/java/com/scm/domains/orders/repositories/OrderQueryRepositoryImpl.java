package com.scm.domains.orders.repositories;
import static com.querydsl.core.group.GroupBy.groupBy;
import static com.querydsl.core.group.GroupBy.list;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.scm.domains.orders.dtos.OrderDTO;
import com.scm.domains.orders.dtos.OrderItemDTO;
import com.scm.domains.orders.entities.QOrder;
import com.scm.domains.orders.entities.QOrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.querydsl.core.group.GroupBy.groupBy;
import static java.util.Collections.list;

@Repository
public class OrderQueryRepositoryImpl implements OrderQueryRepository {

    private final JPAQueryFactory queryFactory;
    private static final Logger _LOGGER = LoggerFactory.getLogger(OrderQueryRepositoryImpl.class);

    public OrderQueryRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Page<OrderDTO> findOrdersPaginated(Pageable pageable) {
        QOrder order = QOrder.order;
        QOrderItem item = QOrderItem.orderItem;

        _LOGGER.warn("Initiating paginated fetch for orders: Page {}, Size {}",
                pageable.getPageNumber(), pageable.getPageSize());

        // Step 1: Fetch the IDs for the current page to avoid Cartesian product issues with LIMIT
        List<UUID> ids = queryFactory
                .select(order.id)
                .from(order)
                .orderBy(order.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }

        // Step 2: Fetch full DTOs for the specific IDs and group items
        Map<UUID, OrderDTO> results = queryFactory
                .from(order)
                .leftJoin(order.items, item)
                .where(order.id.in(ids))
                .transform(groupBy(order.id).as(
                        Projections.constructor(OrderDTO.class,
                                order.id,
                                order.orderNumber,
                                order.customerId,
                                order.status.stringValue(), // Ensures Enum to String conversion
                                order.totalAmount,
                                // Use GroupBy.list to collect multiple items into the DTO's List field
                                list(Projections.constructor(OrderItemDTO.class,
                                        item.id,
                                        item.productId,
                                        item.quantity,
                                        item.unitPrice
                                ))
                        )
                ));

        long total = Optional.ofNullable(
                queryFactory.select(order.count())
                        .from(order)
                        .fetchOne()
        ).orElse(0L);

        // Maintain the original sort order from the ID list
        List<OrderDTO> sortedContent = ids.stream()
                .map(results::get)
                .toList();

        return new PageImpl<>(sortedContent, pageable, total);
    }
}