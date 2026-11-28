package ru.projectx.contracts.repository.issuance;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;

public interface IssuanceRequestRepository extends JpaRepository<IssuanceRequest, UUID> {

    @Query(value = """
            select 1 from (select pg_advisory_xact_lock(hashtextextended(cast(:id as text), 0))) request_lock
            """, nativeQuery = true)
    int lockById(@Param("id") UUID id);
}
