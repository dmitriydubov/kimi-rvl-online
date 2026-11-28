package ru.projectx.contracts.service.status;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.message.status.StatusEvents;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.service.outbox.OutboxDrafts;
import ru.projectx.contracts.service.outbox.OutboxJournal;

@Service
public class RequestStatusPublication {

    static final String AGGREGATE_TYPE = "INDIVIDUAL_REQUEST";

    private final IssuanceRequestRepository requests;
    private final IssuanceSnapshots snapshots;
    private final StatusMessages messages;
    private final OutboxJournal journal;
    private final Clock clock;

    public RequestStatusPublication(IssuanceRequestRepository requests, IssuanceSnapshots snapshots,
            StatusMessages messages, OutboxJournal journal, Clock clock) {
        this.requests = requests;
        this.snapshots = snapshots;
        this.messages = messages;
        this.journal = journal;
        this.clock = clock;
    }

    public IssuanceSnapshot watch(UUID individualRequestId) {
        return snapshots.current(individualRequestId);
    }

    public void afterTask(IssuanceSnapshot before, UUID individualRequestId, List<RequestParticipant> composition,
            Instant now) {
        IssuanceRequest request = requestOf(individualRequestId, now);
        request.takeComposition(composition, now);
        publishIfChanged(request, before, now);
    }

    public void afterRecordChange(IssuanceSnapshot before, Instant now) {
        publishIfChanged(requestOf(before.individualRequestId(), now), before, now);
    }

    @Transactional
    public void afterCancellation(UUID individualRequestId, Instant now) {
        requests.lockById(individualRequestId);
        IssuanceRequest request = requestOf(individualRequestId, now);
        request.cancel(now);
        record(request, snapshots.current(individualRequestId), now);
    }

    private void publishIfChanged(IssuanceRequest request, IssuanceSnapshot before, Instant now) {
        IssuanceSnapshot after = snapshots.current(request.individualRequestId());
        if (StatusTransitions.publishAfterChange(before, after)) {
            record(request, after, now);
        }
    }

    private IssuanceRequest requestOf(UUID individualRequestId, Instant now) {
        return requests.findById(individualRequestId)
                .orElseGet(() -> requests.save(IssuanceRequest.start(individualRequestId, now)));
    }

    private void record(IssuanceRequest request, IssuanceSnapshot snapshot, Instant now) {
        UUID individualRequestId = request.individualRequestId();
        MessageMeta meta = new MessageMeta(UUID.randomUUID(), individualRequestId, StatusEvents.CONTRACT_STATUS_TOPIC,
                StatusEvents.CONTRACT_STATUS_CHANGED, StatusEvents.SCHEMA_VERSION,
                OffsetDateTime.ofInstant(now, clock.getZone()), request.nextSnapshotVersion(now), StatusEvents.SOURCE);
        journal.record(OutboxDrafts.of(AGGREGATE_TYPE, individualRequestId, meta, messages.write(snapshot, meta)), now);
    }
}
