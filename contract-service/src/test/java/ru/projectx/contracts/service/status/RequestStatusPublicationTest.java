package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class RequestStatusPublicationTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final RequestParticipant INSURED = new RequestParticipant(Tasks.INSURED, DocumentKind.NONE);
    private static final RequestParticipant SPOUSE_KM = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final RequestParticipant CHILD_LM = new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY);
    private static final IssuanceSnapshot NOTHING = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(), List.of(),
            List.of(), List.of());
    private static final IssuanceSnapshot COMPLETED = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED),
            List.of(), List.of(), List.of());

    private final IssuanceRequestRepository requests = mock();
    private final IssuanceSnapshots snapshots = mock();
    private final StatusMessages messages = mock();
    private final OutboxJournal journal = mock();
    private final RequestStatusPublication publication = new RequestStatusPublication(requests, snapshots, messages,
            journal, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void watch_isTheCurrentSnapshotOfTheRequest() {
        given(snapshots.current(Tasks.REQUEST)).willReturn(NOTHING);

        assertThat(publication.watch(Tasks.REQUEST)).isSameAs(NOTHING);
    }

    @Test
    void firstTask_startsTheRequestTakesTheCompositionAndRecordsTheFirstSnapshot() {
        IssuanceRequest managed = IssuanceRequest.start(Tasks.REQUEST, NOW);
        given(requests.findById(Tasks.REQUEST)).willReturn(Optional.empty());
        given(requests.save(any())).willReturn(managed);
        given(snapshots.current(Tasks.REQUEST)).willReturn(COMPLETED);
        given(messages.write(eq(COMPLETED), any())).willReturn("{\"data\": {}}");

        publication.afterTask(NOTHING, Tasks.REQUEST, List.of(INSURED, SPOUSE_KM, CHILD_LM), NOW);

        ArgumentCaptor<IssuanceRequest> started = ArgumentCaptor.captor();
        verify(requests).save(started.capture());
        assertThat(started.getValue().individualRequestId()).isEqualTo(Tasks.REQUEST);
        assertThat(started.getValue().participants()).as("несохранённый экземпляр не меняется").isEmpty();
        assertThat(managed.participants()).containsExactly(INSURED, SPOUSE_KM, CHILD_LM);
        assertThat(managed.snapshotVersion()).isEqualTo(1);
        ArgumentCaptor<OutboxDraft> draft = ArgumentCaptor.captor();
        verify(journal).record(draft.capture(), eq(NOW));
        assertThat(draft.getValue().aggregateType()).isEqualTo("INDIVIDUAL_REQUEST");
        assertThat(draft.getValue().aggregateId()).isEqualTo(Tasks.REQUEST);
        assertThat(draft.getValue().payload()).isEqualTo("{\"data\": {}}");
        assertThat(draft.getValue().envelope().correlationId()).isEqualTo(Tasks.REQUEST);
        assertThat(draft.getValue().envelope().topic()).isEqualTo("rvl.contract-status.changed");
        assertThat(draft.getValue().envelope().eventType()).isEqualTo("ContractStatusChanged");
        assertThat(draft.getValue().envelope().entityVersion()).isEqualTo(1);
        ArgumentCaptor<MessageMeta> meta = ArgumentCaptor.captor();
        verify(messages).write(eq(COMPLETED), meta.capture());
        assertThat(meta.getValue().messageId()).isEqualTo(draft.getValue().envelope().messageId());
        assertThat(meta.getValue().createdAt()).isEqualTo(OffsetDateTime.parse("2026-02-10T07:35:00Z"));
        assertThat(meta.getValue().schemaVersion()).isEqualTo("1.0");
        assertThat(meta.getValue().source()).isEqualTo("RVL-CONTRACTS");
        assertThat(meta.getValue().entityVersion()).isEqualTo(1);
    }

    @Test
    void taskWithoutPublishableChange_recordsNothingButKeepsTheComposition() {
        IssuanceRequest request = IssuanceRequest.start(Tasks.REQUEST, NOW);
        request.nextSnapshotVersion(NOW);
        given(requests.findById(Tasks.REQUEST)).willReturn(Optional.of(request));
        given(snapshots.current(Tasks.REQUEST)).willReturn(COMPLETED);

        publication.afterTask(COMPLETED, Tasks.REQUEST, List.of(INSURED), NOW);

        verify(journal, never()).record(any(), any());
        assertThat(request.participants()).containsExactly(INSURED);
        assertThat(request.snapshotVersion()).isEqualTo(1);
    }

    @Test
    void recordChange_keepsTheCompositionAndRecordsTheNextSnapshotWhenItBecamePublishable() {
        IssuanceRequest request = IssuanceRequest.start(Tasks.REQUEST, NOW);
        request.takeComposition(List.of(INSURED, CHILD_LM), NOW);
        request.nextSnapshotVersion(NOW);
        given(requests.findById(Tasks.REQUEST)).willReturn(Optional.of(request));
        IssuanceSnapshot inProgress = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED, CHILD_LM), List.of(),
                List.of(Issuances.policy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD)), List.of());
        LmPolicy issued = Issuances.policy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD);
        issued.submit(NOW);
        issued.markIssued(new IssuedPolicy(UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012"), "LM-000012204",
                UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0"), "0555123456"), NOW);
        IssuanceSnapshot completed = new IssuanceSnapshot(Tasks.REQUEST, false, List.of(INSURED, CHILD_LM), List.of(),
                List.of(issued), List.of());
        given(snapshots.current(Tasks.REQUEST)).willReturn(completed);
        given(messages.write(eq(completed), any())).willReturn("{}");

        publication.afterRecordChange(inProgress, NOW);

        assertThat(request.participants()).containsExactly(INSURED, CHILD_LM);
        ArgumentCaptor<OutboxDraft> draft = ArgumentCaptor.captor();
        verify(journal).record(draft.capture(), eq(NOW));
        assertThat(draft.getValue().envelope().entityVersion()).isEqualTo(2);
    }

    @Test
    void recordChangeWithoutPublishableChange_recordsNothing() {
        given(requests.findById(Tasks.REQUEST)).willReturn(Optional.of(IssuanceRequest.start(Tasks.REQUEST, NOW)));
        given(snapshots.current(Tasks.REQUEST)).willReturn(COMPLETED);

        publication.afterRecordChange(COMPLETED, NOW);

        verify(journal, never()).record(any(), any());
    }

    @Test
    void cancellation_marksTheRequestCancelledAndAlwaysRecordsTheNextSnapshot() {
        IssuanceRequest request = IssuanceRequest.start(Tasks.REQUEST, NOW);
        request.nextSnapshotVersion(NOW);
        given(requests.findById(Tasks.REQUEST)).willReturn(Optional.of(request));
        IssuanceSnapshot cancelled = new IssuanceSnapshot(Tasks.REQUEST, true, List.of(), List.of(), List.of(), List.of());
        given(snapshots.current(Tasks.REQUEST)).willReturn(cancelled);
        given(messages.write(eq(cancelled), any())).willReturn("{}");

        publication.afterCancellation(Tasks.REQUEST, NOW);

        assertThat(request.cancelled()).isTrue();
        InOrder order = inOrder(requests);
        order.verify(requests).lockById(Tasks.REQUEST);
        order.verify(requests).findById(Tasks.REQUEST);
        ArgumentCaptor<OutboxDraft> draft = ArgumentCaptor.captor();
        verify(journal).record(draft.capture(), eq(NOW));
        assertThat(draft.getValue().envelope().entityVersion()).isEqualTo(2);
        verify(requests, never()).save(any());
    }

    @Test
    void cancellationBeforeAnyTask_startsTheRequestWithAnEmptyComposition() {
        given(requests.findById(Tasks.REQUEST)).willReturn(Optional.empty());
        given(requests.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        IssuanceSnapshot cancelled = new IssuanceSnapshot(Tasks.REQUEST, true, List.of(), List.of(), List.of(), List.of());
        given(snapshots.current(Tasks.REQUEST)).willReturn(cancelled);
        given(messages.write(eq(cancelled), any())).willReturn("{}");

        publication.afterCancellation(Tasks.REQUEST, NOW);

        ArgumentCaptor<IssuanceRequest> started = ArgumentCaptor.captor();
        verify(requests).save(started.capture());
        assertThat(started.getValue().cancelled()).isTrue();
        assertThat(started.getValue().participants()).isEmpty();
        verify(journal).record(any(), eq(NOW));
    }
}
