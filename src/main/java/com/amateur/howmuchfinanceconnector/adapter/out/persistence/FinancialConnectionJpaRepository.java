package com.amateur.howmuchfinanceconnector.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialConnectionJpaRepository extends JpaRepository<FinancialConnectionJpaEntity, UUID> {
}
