package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.RowRaces;

@IntegrationTest
class AttachmentRegistryRaceIT {

    private static final String CANCEL_WHILE_HOLDING_REQUEST_LOCK = """
            with request as (select cast(? as uuid) as id),
                 locked as (select pg_advisory_xact_lock(hashtextextended(cast(id as text), 0)) from request)
            insert into issuance_requests (individual_request_id, snapshot_version, cancelled_at, updated_at)
            select id, 0, now(), now() from request where exists (select 1 from locked)
            """;

    @Autowired
    private AttachmentRegistry registry;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    private final UUID request = UUID.randomUUID();
    private final UUID participant = UUID.randomUUID();
    private final UUID attachment = UUID.randomUUID();
    private RowRaces races;

    @BeforeEach
    void raceStand() {
        races = new RowRaces(dataSource, jdbc);
    }

    @AfterEach
    void removeRecords() {
        jdbc.sql("delete from contract_documents where attachment_id = :attachment").param("attachment", attachment).update();
        jdbc.sql("delete from issuance_requests where individual_request_id = :request").param("request", request).update();
    }

    @Test
    void cancellationCommittedWhileRegistrationWaitsForTheRequestLock_rejectsTheAttachment() throws SQLException {
        races.commitOnceTheOtherPathWaitsForTheRow(CANCEL_WHILE_HOLDING_REQUEST_LOCK, request,
                () -> assertThatThrownBy(this::register).isInstanceOf(RequestCancelledException.class));

        assertThat(jdbc.sql("select count(*) from contract_documents where attachment_id = :attachment")
                .param("attachment", attachment).query(Integer.class).single()).isZero();
    }

    private void register() {
        transaction.executeWithoutResult(status -> registry.register(command(), file()));
    }

    private AttachmentCommand command() {
        CertificateUpload upload = new CertificateUpload(request, participant, attachment, "image/jpeg", 76,
                Uploads.sized("image/jpeg", 76).content());
        return AttachmentCommands.of(upload);
    }

    private StoredFile file() {
        return new StoredFile("certificates/race/" + attachment + ".jpg", "a".repeat(64), 76, "image/jpeg");
    }
}
