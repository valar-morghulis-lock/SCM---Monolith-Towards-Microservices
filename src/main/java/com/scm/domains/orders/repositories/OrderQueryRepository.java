package com.scm.domains.orders.repositories;

import com.scm.domains.orders.dtos.OrderDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderQueryRepository {
    Page<OrderDTO> findOrdersPaginated(Pageable pageable);
}