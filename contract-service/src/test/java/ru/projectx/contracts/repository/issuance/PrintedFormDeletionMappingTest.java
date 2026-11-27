package ru.projectx.contracts.repository.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import ru.projectx.contracts.support.PostgresTestcontainer;

@SpringBootTest
@Import(PostgresTestcontainer.class)
@TestPropertySource("classpath:printed-form-deletion-test.properties")
class PrintedFormDeletionMappingTest {

    private static final Instant DELETED_AT = Instant.parse("2026-11-20T10:15:30Z");
    private static final String DELETE_REQUEST = "delete-request-1";

    @Autowired
    private PrintedFormRepository documents;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyDocuments() {
        jdbc.sql("truncate table contract_documents cascade").update();
    }

    @Test
    void deletedConsent_exposesItsDeletionTimeAndRequest() {
        UUID document = UUID.randomUUID();
        UUID policy = UUID.randomUUID();
        UUID familyMember = UUID.randomUUID();
        UUID attachment = UUID.randomUUID();
        jdbc.sql("insert into contract_documents (id, kind, doc_type, version, created_at, policy_id, "
                        + "family_member_id, attachment_id, file_key, file_hash, file_size, content_type, "
                        + "deleted_at, deleted_request_id) values (:document, 'FINAL', 'PD_CONSENT', 1, now(), "
                        + ":policy, :family, :attachment, 'k', 'h', 1, 'image/png', :deletedAt, :deleteRequest)")
                .param("document", document).param("policy", policy).param("family", familyMember)
                .param("attachment", attachment).param("deletedAt", Timestamp.from(DELETED_AT))
                .param("deleteRequest", DELETE_REQUEST)
                .update();

        assertThat(documents.findById(document)).get().satisfies(consent -> {
            assertThat(consent.deletedAt()).contains(DELETED_AT);
            assertThat(consent.deletedRequestId()).contains(DELETE_REQUEST);
        });
    }
}
