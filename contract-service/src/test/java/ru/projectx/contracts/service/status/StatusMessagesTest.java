package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedForms;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.document.UskDocument;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class StatusMessagesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final UUID MESSAGE_ID = UUID.fromString("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442");
    private static final String CONTRACT_ID = "01960000-0000-7000-8000-000000000001";
    private static final String POLICY_ID = "01960000-0000-7000-8000-000000000002";
    private static final RequestParticipant INSURED = new RequestParticipant(Tasks.INSURED, DocumentKind.NONE);
    private static final RequestParticipant SPOUSE_KM = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final RequestParticipant CHILD_LM = new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY);
    private static final MessageMeta META = new MessageMeta(MESSAGE_ID, Tasks.REQUEST, "rvl.contract-status.changed",
            "ContractStatusChanged", "1.0", OffsetDateTime.parse("2026-02-10T10:35:00+03:00"), 3, "RVL-CONTRACTS");

    private final JsonMapper json = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final StatusMessages messages = new StatusMessages(json);

    @Test
    void message_carriesTheEnvelopeAndTheRequestStateInSnakeCase() {
        JsonNode body = json.readTree(messages.write(
                new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED), List.of(), List.of(), List.of()), META));

        JsonNode meta = body.path("meta");
        assertThat(meta.path("message_id").stringValue()).isEqualTo(MESSAGE_ID.toString());
        assertThat(meta.path("correlation_id").stringValue()).isEqualTo(Tasks.REQUEST.toString());
        assertThat(meta.path("topic").stringValue()).isEqualTo("rvl.contract-status.changed");
        assertThat(meta.path("event_type").stringValue()).isEqualTo("ContractStatusChanged");
        assertThat(meta.path("schema_version").stringValue()).isEqualTo("1.0");
        assertThat(meta.path("created_at").stringValue()).isEqualTo("2026-02-10T10:35:00+03:00");
        assertThat(meta.path("entity_version").intValue()).isEqualTo(3);
        assertThat(meta.path("source").stringValue()).isEqualTo("RVL-CONTRACTS");
        JsonNode data = body.path("data");
        assertThat(data.path("individual_request_id").stringValue()).isEqualTo(Tasks.REQUEST.toString());
        assertThat(data.path("status").stringValue()).isEqualTo("COMPLETED");
        JsonNode participant = data.path("participants").path(0);
        assertThat(participant.path("id").stringValue()).isEqualTo(Tasks.INSURED.toString());
        assertThat(participant.path("document_kind").stringValue()).isEqualTo("NONE");
        assertThat(participant.path("km_contract_state").stringValue()).isEqualTo("NOT_REQUIRED");
        assertThat(participant.path("payment_status").stringValue()).isEqualTo("NOT_REQUIRED");
        assertThat(participant.path("documents").isArray()).isTrue();
        assertThat(participant.path("documents")).isEmpty();
        assertThat(participant.has("km_contract_id")).isFalse();
        assertThat(participant.has("km_contract_number")).isFalse();
        assertThat(participant.has("policy_id")).isFalse();
        assertThat(participant.has("policy_number")).isFalse();
        assertThat(participant.has("error_code")).isFalse();
        assertThat(participant.has("error_message")).isFalse();
    }

    @Test
    void kmContractInErrorBeforeDraft_isCancelledWithNumberIdentifierAndError() {
        Contract failed = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
        failed.markError(new IssueError("RELATION_TYPE_REQUIRED",
                "поле participants[1].personal_data.relation_type обязательно для родственника"), NOW);

        JsonNode participant = json.readTree(messages.write(new IssuanceSnapshot(Tasks.REQUEST, false,
                List.of(SPOUSE_KM), List.of(failed), List.of(), List.of()), META)).path("data").path("participants").path(0);

        assertThat(participant.path("document_kind").stringValue()).isEqualTo("KM_CONTRACT");
        assertThat(participant.path("km_contract_id").stringValue()).isEqualTo(CONTRACT_ID);
        assertThat(participant.path("km_contract_state").stringValue()).isEqualTo("CANCELLED");
        assertThat(participant.path("km_contract_number").stringValue()).isEqualTo("DKM-2026-000001");
        assertThat(participant.path("payment_status").stringValue()).isEqualTo("NOT_REQUIRED");
        assertThat(participant.path("error_code").stringValue()).isEqualTo("RELATION_TYPE_REQUIRED");
        assertThat(participant.path("error_message").stringValue())
                .isEqualTo("поле participants[1].personal_data.relation_type обязательно для родственника");
    }

    @Test
    void cancelledRecordAfterError_keepsNoErrorInTheMessage() {
        LmPolicy policy = Issuances.policy(POLICY_ID, Tasks.CHILD);
        policy.markError(new IssueError("UNKNOWN_DOCUMENT_TYPE", "недопустимое значение поля"), NOW);
        policy.cancel(new Cancellation(CancelReason.REQUEST_CANCELLED, null, NOW), NOW);

        JsonNode body = json.readTree(messages.write(new IssuanceSnapshot(Tasks.REQUEST, true, List.of(CHILD_LM),
                List.of(), List.of(policy), List.of()), META));

        assertThat(body.path("data").path("status").stringValue()).isEqualTo("CANCELLED");
        JsonNode participant = body.path("data").path("participants").path(0);
        assertThat(participant.path("document_kind").stringValue()).isEqualTo("LM_POLICY");
        assertThat(participant.path("km_contract_state").stringValue()).isEqualTo("NOT_REQUIRED");
        assertThat(participant.has("km_contract_id")).isFalse();
        assertThat(participant.has("error_code")).isFalse();
        assertThat(participant.has("error_message")).isFalse();
    }

    @Test
    void lmPolicyInError_carriesItsErrorWithoutContractFields() {
        LmPolicy policy = Issuances.policy(POLICY_ID, Tasks.CHILD);
        policy.markError(new IssueError("UNKNOWN_DOCUMENT_TYPE", "недопустимое значение поля"), NOW);

        JsonNode participant = json.readTree(messages.write(new IssuanceSnapshot(Tasks.REQUEST, false,
                List.of(CHILD_LM), List.of(), List.of(policy), List.of()), META)).path("data").path("participants").path(0);

        assertThat(participant.path("error_code").stringValue()).isEqualTo("UNKNOWN_DOCUMENT_TYPE");
        assertThat(participant.path("error_message").stringValue()).isEqualTo("недопустимое значение поля");
        assertThat(participant.has("km_contract_number")).isFalse();
        assertThat(participant.has("policy_id")).isFalse();
        assertThat(participant.path("documents")).isEmpty();
    }

    @Test
    void issuedLmPolicy_carriesOurPolicyNumberAndFinalFormsWithTheirIdentifiersAndLinks() {
        LmPolicy policy = Issuances.policy(POLICY_ID, Tasks.CHILD);
        policy.submit(NOW);
        policy.markIssued(new IssuedPolicy(UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012"), "LM-000012204",
                UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0"), "0555123456"), NOW);
        PrintedForm form = PrintedForms.issuedFor(PersonOwner.lmPolicy(policy.id()), new UskDocument(PrintedFormType.POLICY,
                "https://s3.sogaz.ru/rvl/lm/2026/02/0555123456.pdf", 96256L), NOW);
        ReflectionTestUtils.setField(form, "id", UUID.fromString("01960000-0000-7000-8000-000000000003"));

        JsonNode body = json.readTree(messages.write(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(CHILD_LM),
                List.of(), List.of(policy), List.of(form)), META));

        assertThat(body.path("data").path("status").stringValue()).isEqualTo("COMPLETED");
        JsonNode participant = body.path("data").path("participants").path(0);
        assertThat(participant.path("document_kind").stringValue()).isEqualTo("LM_POLICY");
        assertThat(participant.path("km_contract_state").stringValue()).isEqualTo("NOT_REQUIRED");
        assertThat(participant.path("policy_id").stringValue()).isEqualTo("13ab5388-61ee-4e21-8a46-c325478b78c0");
        assertThat(participant.path("policy_number").stringValue()).isEqualTo("0555123456");
        assertThat(participant.path("payment_status").stringValue()).isEqualTo("NOT_REQUIRED");
        JsonNode document = participant.path("documents").path(0);
        assertThat(participant.path("documents")).hasSize(1);
        assertThat(document.path("document_id").stringValue()).isEqualTo("01960000-0000-7000-8000-000000000003");
        assertThat(document.path("doc_type").stringValue()).isEqualTo("POLICY");
        assertThat(document.path("kind").stringValue()).isEqualTo("FINAL");
        assertThat(document.path("url").stringValue()).isEqualTo("https://s3.sogaz.ru/rvl/lm/2026/02/0555123456.pdf");
        assertThat(participant.has("error_code")).isFalse();
    }

    @Test
    void paidKmContractRegisteredByUsk_carriesPaidPaymentAndThePolicyIssuedUnderIt() {
        Contract contract = Issuances.paidContract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001",
                Instant.parse("2026-02-10T07:20:15Z"));
        contract.submit(NOW);
        contract.markIssued(new IssuedPolicy(UUID.fromString("a71f3c98-4e20-4a55-93b1-5d0c7e2f8814"), "DKM-000004417",
                UUID.fromString("2c7e9b40-1f5a-4d83-9e6b-0a4c8d2f7e15"), "0987654321"), NOW);
        PrintedForm form = PrintedForms.issuedFor(PersonOwner.contract(contract.id()), new UskDocument(PrintedFormType.CONTRACT,
                "https://s3.sogaz.ru/rvl/km/2026/02/DKM-2026-000001.pdf", 205312L), NOW);
        ReflectionTestUtils.setField(form, "id", UUID.fromString("01960000-0000-7000-8000-000000000004"));

        JsonNode body = json.readTree(messages.write(new IssuanceSnapshot(Tasks.REQUEST, false, List.of(SPOUSE_KM),
                List.of(contract), List.of(), List.of(form)), META));

        assertThat(body.path("data").path("status").stringValue()).isEqualTo("COMPLETED");
        JsonNode participant = body.path("data").path("participants").path(0);
        assertThat(participant.path("document_kind").stringValue()).isEqualTo("KM_CONTRACT");
        assertThat(participant.path("km_contract_id").stringValue()).isEqualTo(CONTRACT_ID);
        assertThat(participant.path("km_contract_state").stringValue()).isEqualTo("ISSUED");
        assertThat(participant.path("km_contract_number").stringValue()).isEqualTo("DKM-2026-000001");
        assertThat(participant.path("payment_status").stringValue()).isEqualTo("PAID");
        assertThat(participant.path("km_policy_id").stringValue()).isEqualTo("2c7e9b40-1f5a-4d83-9e6b-0a4c8d2f7e15");
        assertThat(participant.path("km_policy_number").stringValue()).isEqualTo("0987654321");
        assertThat(participant.has("policy_id")).isFalse();
        assertThat(participant.has("policy_number")).isFalse();
        JsonNode document = participant.path("documents").path(0);
        assertThat(participant.path("documents")).hasSize(1);
        assertThat(document.path("document_id").stringValue()).isEqualTo("01960000-0000-7000-8000-000000000004");
        assertThat(document.path("doc_type").stringValue()).isEqualTo("CONTRACT");
        assertThat(document.path("url").stringValue()).isEqualTo("https://s3.sogaz.ru/rvl/km/2026/02/DKM-2026-000001.pdf");
        assertThat(participant.has("error_code")).isFalse();
    }

    @Test
    void paidKmContractStillWaitingForUsk_isPaidButDrafted_withoutAPolicy() {
        Contract contract = Issuances.paidContract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001",
                Instant.parse("2026-02-10T07:20:15Z"));
        contract.submit(NOW);

        JsonNode participant = json.readTree(messages.write(new IssuanceSnapshot(Tasks.REQUEST, false,
                List.of(SPOUSE_KM), List.of(contract), List.of(), List.of()), META)).path("data").path("participants").path(0);

        assertThat(participant.path("km_contract_state").stringValue()).isEqualTo("DRAFTED");
        assertThat(participant.path("payment_status").stringValue()).isEqualTo("PAID");
        assertThat(participant.has("km_policy_id")).isFalse();
        assertThat(participant.has("km_policy_number")).isFalse();
    }

    @Test
    void formsOfAnotherPolicy_doNotLeakIntoTheParticipant() {
        LmPolicy policy = Issuances.policy(POLICY_ID, Tasks.CHILD);
        PrintedForm foreign = PrintedForms.issuedFor(PersonOwner.lmPolicy(UUID.fromString("01960000-0000-7000-8000-000000000009")),
                new UskDocument(PrintedFormType.POLICY, "https://s3.sogaz.ru/rvl/lm/other.pdf", null), NOW);

        JsonNode participant = json.readTree(messages.write(new IssuanceSnapshot(Tasks.REQUEST, false,
                List.of(CHILD_LM), List.of(), List.of(policy), List.of(foreign)), META)).path("data").path("participants").path(0);

        assertThat(participant.path("documents")).isEmpty();
    }
}
