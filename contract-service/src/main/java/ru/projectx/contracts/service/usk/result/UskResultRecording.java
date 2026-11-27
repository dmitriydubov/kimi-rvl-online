package ru.projectx.contracts.service.usk.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.document.PrintedForms;
import ru.projectx.contracts.entity.document.UskDocument;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.repository.UskRecordRepository;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.IssuanceKind;
import ru.projectx.contracts.service.usk.UskWording;

public class UskResultRecording<R extends IssuanceRecord, S extends IssuanceStatus> {

    private final IssuanceKind<R, S> kind;
    private final UskRecordRepository<R, S> records;
    private final PrintedFormRepository forms;
    private final RequestStatusPublication statuses;
    private final Logger log;

    protected UskResultRecording(IssuanceKind<R, S> kind, UskRecordRepository<R, S> records,
            PrintedFormRepository forms, RequestStatusPublication statuses, Logger log) {
        this.kind = kind;
        this.records = records;
        this.forms = forms;
        this.statuses = statuses;
        this.log = log;
    }

    public ResultExpectation expect(UUID id) {
        R record = load(id);
        if (!kind.requested(record)) {
            throw new ContractViolationException(kind.wording().notRequested());
        }
        return kind.awaiting(record) ? ResultExpectation.AWAITING_RESULT : ResultExpectation.ALREADY_FINISHED;
    }

    public void recordLate(UUID id) {
        warnUnchanged(load(id));
    }

    @Transactional
    public void recordIssued(UUID id, IssuedPolicy issued, List<UskDocument> documents, Instant now) {
        applyResult(id, now, record -> {
            kind.completion().issue(record, issued, now);
            forms.saveAll(documents.stream()
                    .map(document -> PrintedForms.issuedFor(kind.owner().apply(record.id()), document, now))
                    .toList());
        });
    }

    @Transactional
    public void recordFailure(UUID id, IssueError error, Instant now) {
        applyResult(id, now, record -> record.markError(error, now));
    }

    private void applyResult(UUID id, Instant now, Consumer<R> result) {
        R record = records.findLockedById(id).orElseThrow(() -> unknown(id));
        if (!kind.awaiting(record)) {
            warnUnchanged(record);
            return;
        }
        IssuanceSnapshot before = statuses.watch(record.state().request().individualRequestId());
        result.accept(record);
        statuses.afterRecordChange(before, now);
    }

    private R load(UUID id) {
        return records.findById(id).orElseThrow(() -> unknown(id));
    }

    private ContractViolationException unknown(UUID id) {
        return new ContractViolationException(kind.wording().kindGenitive() + " " + id + " в Договорах нет");
    }

    private void warnUnchanged(R record) {
        UskWording wording = kind.wording();
        log.warn("{} {} участника {} заявки {}: {} получен по {} в статусе {}, запись не изменена", wording.kind(),
                record.id(), record.participantId(), record.state().request().individualRequestId(), wording.result(),
                wording.kindDative(), record.status());
    }
}
