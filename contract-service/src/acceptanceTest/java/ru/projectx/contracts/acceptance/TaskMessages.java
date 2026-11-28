package ru.projectx.contracts.acceptance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import io.cucumber.spring.ScenarioScope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class TaskMessages {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final String SOURCE = "RVL-REQUESTS";
    private static final String SCHEMA_VERSION = "1.0";
    private static final String SERVICE = "bf3ef3a5-fa45-4c8e-ab91-3432399fe5d5";
    private static final String DEFAULT_PROGRAM = "2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a";
    private static final String PROGRAM = "программа";
    private static final List<ParticipantDefaults> DEFAULTS = List.of(
            new ParticipantDefaults("Тестов", "Тест", "Тестович", "1984-03-11", "MALE", "RF_PASSPORT", "4510", "123456"),
            new ParticipantDefaults("Тестова", "Теста", "Тестовна", "1988-07-02", "FEMALE", "RF_PASSPORT", "4512", "654321"),
            new ParticipantDefaults("Тестов", "Тестик", "Тестович", "2017-05-30", "MALE", "BIRTH_CERTIFICATE", "IV-M", "512345"),
            new ParticipantDefaults("Проверов", "Провер", "Проверович", "1960-01-15", "MALE", "RF_PASSPORT", "4501", "111222"));

    private final JsonMapper json;
    private final RequestFixture requests;
    private final Clock clock;

    public TaskMessages(JsonMapper json, RequestFixture requests, Clock clock) {
        this.json = json;
        this.requests = requests;
        this.clock = clock;
    }

    record ParticipantDefaults(String lastName, String firstName, String middleName, String birthDate, String gender,
            String documentType, String series, String number) {
    }

    ObjectNode upserted(String messageId, String requestId, int version, List<Map<String, String>> participants) {
        ObjectNode root = json.createObjectNode();
        meta(root, messageId, requestId, "rvl.contract-task.upserted", "ContractTaskUpserted", version);
        Map<String, String> request = requests.of(requestId);
        ObjectNode data = root.putObject("data")
                .put("individual_request_id", requestId)
                .put("individual_request_number", request.get("номер"))
                .put("group_request_id", request.get("массовая заявка"))
                .put("insurance_contract_id", request.get("договор страхования"))
                .put("insurer_policy_id", request.get("полис страхователя"))
                .put("lpu_external_id", request.get("ЛПУ"))
                .put("date_from", request.get("заезд"))
                .put("date_to", request.get("выезд"));
        ArrayNode array = data.putArray("participants");
        for (int index = 0; index < participants.size(); index++) {
            participant(array.addObject(), participants.get(index), index, request.get("полис страхователя"));
        }
        return root;
    }

    ObjectNode cancelled(String messageId, String requestId, int version, Map<String, String> fields) {
        ObjectNode root = json.createObjectNode();
        meta(root, messageId, requestId, "rvl.contract-task.cancelled", "ContractTaskCancelled", version);
        ObjectNode data = root.putObject("data")
                .put("individual_request_id", requestId)
                .put("individual_request_number", requests.of(requestId).get("номер"))
                .put("cancelled_at", StepTables.required(fields, "аннулировано"))
                .put("cancel_reason", StepTables.required(fields, "причина"));
        StepTables.cell(fields, "комментарий").ifPresent(comment -> data.put("cancel_comment", comment));
        return root;
    }

    private void meta(ObjectNode root, String messageId, String requestId, String topic, String eventType, int version) {
        root.putObject("meta")
                .put("message_id", messageId)
                .put("correlation_id", requestId)
                .put("topic", topic)
                .put("event_type", eventType)
                .put("schema_version", SCHEMA_VERSION)
                .put("created_at", OffsetDateTime.now(clock.withZone(MOSCOW)).toString())
                .put("entity_version", version)
                .put("source", SOURCE);
    }

    private static void participant(ObjectNode node, Map<String, String> row, int index, String insurerPolicy) {
        boolean relative = "да".equals(StepTables.required(row, "родственник"));
        ParticipantDefaults defaults = DEFAULTS.get(index % DEFAULTS.size());
        node.put("id", UploadFixtures.participant(StepTables.required(row, "участник")))
                .put("is_relative", relative)
                .put("policy_id", relative ? null : insurerPolicy)
                .put("service_external_id", SERVICE)
                .put("amount_total", new BigDecimal("168000.00"))
                .put("limit_amount", new BigDecimal("150000.00"))
                .put("copay_percent", 0)
                .put("amount_due", new BigDecimal(StepTables.required(row, "доплата")));
        program(node, row);
        ObjectNode person = node.putObject("personal_data")
                .put("last_name", StepTables.cell(row, "фамилия").orElse(defaults.lastName()))
                .put("first_name", StepTables.cell(row, "имя").orElse(defaults.firstName()))
                .put("middle_name", StepTables.cell(row, "отчество").orElse(defaults.middleName()))
                .put("birth_date", StepTables.cell(row, "дата рождения").orElse(defaults.birthDate()))
                .put("gender", StepTables.cell(row, "пол").orElse(defaults.gender()));
        StepTables.cell(row, "родство").ifPresent(relation -> person.put("relation_type", relation));
        person.putObject("document")
                .put("type", StepTables.cell(row, "документ").orElse(defaults.documentType()))
                .put("series", StepTables.cell(row, "серия").orElse(defaults.series()))
                .put("number", StepTables.cell(row, "номер документа").orElse(defaults.number()))
                .put("issued_by", "Тестовый орган")
                .put("issued_at", "2010-04-20")
                .put("division_code", "770-001");
        person.put("address", "Тестовый адрес, " + (index + 1));
    }

    private static void program(ObjectNode node, Map<String, String> row) {
        if (!row.containsKey(PROGRAM)) {
            node.put("program_id", DEFAULT_PROGRAM);
            return;
        }
        StepTables.cell(row, PROGRAM).ifPresent(program -> node.put("program_id", program));
    }

    byte[] bytes(JsonNode body) {
        return json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
    }

    JsonNode read(byte[] body) {
        return json.readTree(body);
    }
}
