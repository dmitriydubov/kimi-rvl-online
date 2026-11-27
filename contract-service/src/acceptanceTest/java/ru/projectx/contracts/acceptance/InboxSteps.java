package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ParameterType;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import ru.projectx.contracts.service.inbox.processing.InboxProcessor;
import ru.projectx.contracts.service.retention.InboxPayloadRetention;
import ru.projectx.contracts.service.retention.OutboxPayloadRetention;
import ru.projectx.contracts.service.retention.QuarantinePayloadRetention;

public class InboxSteps {

    private static final Duration PATIENCE = Duration.ofSeconds(40);
    private static final Set<String> PENDING = Set.of("ждёт разбора", "отложено до повтора");
    private static final String CORRELATION_ID = "correlation_id";
    private static final String PROCESSED = "обработано";
    private static final String SKIPPED = "пропущено";

    private final InboxJournal journal;
    private final StandQueues queues;
    private final TaskMessages messages;
    private final PublishedMessages published;
    private final InboxProcessor processor;
    private final InboxPayloadRetention erasure;
    private final OutboxPayloadRetention outboxErasure;
    private final QuarantinePayloadRetention quarantineErasure;
    private final OutboxRecords outbox;
    private final ErasureStates statesBeforeErasure;
    private final PaymentOrderRecords orders;

    public InboxSteps(InboxJournal journal, StandQueues queues, TaskMessages messages, PublishedMessages published,
            InboxProcessor processor, InboxPayloadRetention erasure, OutboxPayloadRetention outboxErasure,
            QuarantinePayloadRetention quarantineErasure, OutboxRecords outbox, ErasureStates statesBeforeErasure,
            PaymentOrderRecords orders) {
        this.orders = orders;
        this.journal = journal;
        this.queues = queues;
        this.messages = messages;
        this.published = published;
        this.processor = processor;
        this.erasure = erasure;
        this.outboxErasure = outboxErasure;
        this.quarantineErasure = quarantineErasure;
        this.outbox = outbox;
        this.statesBeforeErasure = statesBeforeErasure;
    }

    @ParameterType(MessageSender.PATTERN)
    public MessageSender sender(String alias) {
        return MessageSender.called(alias);
    }

    @Тогда("очередь {string} пуста и у неё есть потребитель")
    public void queueEmptyWithConsumer(String queueName) {
        StandQueue target = queues.called(queueName);
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> {
            var state = target.state();
            assertThat(state.getQueue()).isEqualTo(target.name());
            assertThat(state.getMessageCount()).as("сообщений в очереди %s", target.name()).isZero();
            assertThat(state.getConsumerCount()).as("потребителей очереди %s", target.name()).isPositive();
        });
    }

    @Тогда("сообщение {sender} {string} ждёт разбора")
    public void awaitsProcessing(MessageSender sender, String messageId) {
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> assertThat(journal.find(messageId)).isPresent());
        assertThat(entry(sender, messageId).outcome()).isEqualTo("ждёт разбора");
    }

    @Тогда("сообщение {sender} {string} обработано")
    public void processed(MessageSender sender, String messageId) {
        assertThat(entry(sender, messageId).outcome()).isEqualTo("обработано");
    }

    @Тогда("сообщение {sender} {string} пропущено с причиной {string}")
    public void skipped(MessageSender sender, String messageId, String reason) {
        InboxJournal.Entry entry = entry(sender, messageId);
        assertThat(entry.outcome()).isEqualTo("пропущено");
        assertThat(entry.errorMessage()).isEqualTo(reason);
    }

    @Тогда("сообщение {sender} {string} отложено до повтора после попытки {int} с причиной {string}")
    public void retrying(MessageSender sender, String messageId, int attempt, String reason) {
        InboxJournal.Entry entry = entry(sender, messageId);
        assertThat(entry.outcome()).isEqualTo("отложено до повтора");
        assertThat(entry.attempts()).isEqualTo(attempt);
        assertThat(entry.errorMessage()).isEqualTo(reason);
        assertThat(entry.nextAttemptAt()).isNotNull();
    }

    @Тогда("сообщение {sender} {string} отклонено окончательно после попытки {int} с причиной {string}")
    public void rejected(MessageSender sender, String messageId, int attempt, String reason) {
        InboxJournal.Entry entry = entry(sender, messageId);
        assertThat(entry.outcome()).isEqualTo("отклонено окончательно");
        assertThat(entry.attempts()).isEqualTo(attempt);
        assertThat(entry.errorMessage()).isEqualTo(reason);
    }

    @Тогда("запись о сообщении {string} в журнале входящих:")
    public void entryRequisites(String messageId, Map<String, String> expected) {
        StepTables.onlyKnown(expected, Set.of("тип события", "заявка", "версия", "тело", CORRELATION_ID));
        InboxJournal.Entry entry = entry(messageId);
        StepTables.cell(expected, CORRELATION_ID).ifPresent(source ->
                assertThat(entry.correlationId()).as(CORRELATION_ID).isEqualTo(correlationOf(source, entry)));
        StepTables.cell(expected, "тип события").ifPresent(type -> assertThat(entry.eventType()).isEqualTo(type));
        StepTables.cell(expected, "заявка").ifPresent(request ->
                assertThat(entry.correlationId()).isEqualTo(UUID.fromString(request)));
        StepTables.cell(expected, "версия").ifPresent(version ->
                assertThat(entry.entityVersion()).isEqualTo(Integer.parseInt(version)));
        StepTables.cell(expected, "тело").ifPresent(body -> payload(messageId, body));
    }

    @Тогда("тело сообщения {string} стёрто")
    public void payloadErased(String messageId) {
        payload(messageId, "стёрто");
    }

    @Тогда("тело сообщения {string} сохранено")
    public void payloadKept(String messageId) {
        payload(messageId, "сохранено");
    }

    @Тогда("в журнале входящих сообщений записи о сообщении {string} нет")
    public void noEntry(String messageId) {
        assertThat(journal.find(messageId)).isEmpty();
    }

    @Тогда("сообщения {sender} в журнале входящих в порядке приёма — {string}")
    public void entriesInOrder(MessageSender sender, String messageIds) {
        List<String> expected = list(messageIds).stream().map(sender::messageId).toList();
        Awaitility.await().atMost(PATIENCE).until(() -> received(sender).size() >= expected.size());
        assertThat(received(sender)).containsExactlyElementsOf(expected);
    }

    private List<String> received(MessageSender sender) {
        return journal.inOrderOfReceipt().stream()
                .filter(entry -> sender.sends(entry.eventType()))
                .map(entry -> entry.messageId().toString())
                .toList();
    }

    @Тогда("исходы сообщений {sender} в порядке приёма — {string}")
    public void outcomesInOrder(MessageSender sender, String outcomes) {
        assertThat(journal.inOrderOfReceipt()).filteredOn(entry -> sender.sends(entry.eventType()))
                .extracting(InboxJournal.Entry::outcome)
                .containsExactlyElementsOf(list(outcomes));
    }

    @Тогда("обработанная версия заявки {string} — {int}")
    public void processedVersion(String requestId, int version) {
        assertThat(journal.processedVersion(requestId)).contains(version);
    }

    @Когда("Договоры разбирают присланные сообщения")
    public void processingPass() {
        awaitReceipts();
        processor.processDue();
    }

    @Дано("Договоры разобрали присланные сообщения")
    public void everythingProcessed() {
        awaitReceipts();
        List<InboxJournal.Entry> pending = journal.inOrderOfReceipt().stream()
                .filter(entry -> PENDING.contains(entry.outcome()))
                .toList();
        if (pending.isEmpty() && !published.repeatedSincePass().isEmpty()) {
            awaitRepeatsTaken();
            return;
        }
        assertThat(pending).as("сообщения, которые ждёт проход").isNotEmpty();
        processor.processDue();
        published.passDone();
        pending.forEach(entry -> assertThat(entry(entry.messageId().toString()).outcome())
                .as("исход сообщения %s; причина %s", entry.messageId(),
                        entry(entry.messageId().toString()).errorMessage())
                .isIn(outcomesOfProcessed(entry.eventType())));
    }

    private void awaitRepeatsTaken() {
        Map<String, String> repeated = published.repeatedSincePass();
        repeated.keySet().forEach(messageId -> assertThat(entry(messageId).outcome())
                .as("исход сообщения %s, присланного повторно", messageId).isIn(PROCESSED, SKIPPED));
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> repeated.values().forEach(alias ->
                assertThat(queues.called(alias).state().getMessageCount()).as("сообщений в очереди «%s»", alias).isZero()));
        published.passDone();
    }

    private static Set<String> outcomesOfProcessed(String eventType) {
        return MessageSender.ORDERING.sends(eventType) ? Set.of(PROCESSED, SKIPPED) : Set.of(PROCESSED);
    }

    @Когда("Договоры выполняют проход стирания персональных данных")
    public void erasurePass() {
        statesBeforeErasure.clear();
        journal.inOrderOfReceipt().forEach(entry ->
                statesBeforeErasure.remember(entry.messageId(), entry.status(), entry.attempts()));
        outbox.all().forEach(entry -> statesBeforeErasure.remember(entry.messageId(), entry.status(), entry.attempts()));
        erasure.eraseExpired();
        outboxErasure.eraseExpired();
        quarantineErasure.eraseExpired();
    }

    @Тогда("статус и число попыток сообщения {string} те же, что до прохода стирания")
    public void stateUnchangedByErasure(String messageId) {
        InboxJournal.Entry entry = entry(messageId);
        assertThat(statesBeforeErasure.unchanged(entry.messageId(), entry.status(), entry.attempts()))
                .as("статус и попытки сообщения %s до и после стирания", messageId).isTrue();
    }

    private void awaitReceipts() {
        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                published.messageIds().forEach(messageId -> assertThat(journal.find(messageId)).isPresent()));
    }

    private void payload(String messageId, String expectation) {
        InboxJournal.Entry entry = entry(messageId);
        if ("стёрто".equals(expectation)) {
            assertThat(entry.payload()).as("тело сообщения %s", messageId).isNull();
            return;
        }
        assertThat(expectation).as("ожидание о теле").isEqualTo("сохранено");
        assertThat(entry.payload()).as("тело сообщения %s", messageId).isNotNull();
        assertThat(messages.read(entry.payload().getBytes())).isEqualTo(messages.read(published.of(messageId)));
    }

    private InboxJournal.Entry entry(String messageId) {
        return journal.find(messageId)
                .orElseThrow(() -> new AssertionError("в журнале входящих нет записи о сообщении " + messageId));
    }

    private UUID correlationOf(String source, InboxJournal.Entry entry) {
        return switch (source) {
            case "как у заказа" -> UUID.fromString(orders.onlyOrder().get("id"));
            case "как message_id" -> entry.messageId();
            default -> throw new IllegalArgumentException("стенд не знает источника correlation_id «" + source + "»");
        };
    }

    private InboxJournal.Entry entry(MessageSender sender, String name) {
        String messageId = sender.messageId(name);
        InboxJournal.Entry entry = entry(messageId);
        assertThat(sender.sends(entry.eventType()))
                .as("сообщение %s с типом события %s прислано %s", messageId, entry.eventType(), sender.alias())
                .isTrue();
        return entry;
    }

    private static List<String> list(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",")).map(String::trim).toList();
    }
}
