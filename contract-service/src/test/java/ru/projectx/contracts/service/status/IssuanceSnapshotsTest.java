package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedForms;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.document.UskDocument;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.support.Invoices;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class IssuanceSnapshotsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final RequestParticipant SPOUSE_KM = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final RequestParticipant CHILD_LM = new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY);
    private static final Contract CONTRACT = Issuances.contract("3b1f2c7e-5a4d-4c8b-9e2f-1a0b3c4d5e6f", Tasks.SPOUSE,
            "КМ-000001");
    private static final LmPolicy POLICY = Issuances.policy("8c7d6e5f-4a3b-4c2d-8e1f-0a9b8c7d6e5f", Tasks.CHILD);
    private static final Contract INVOICED = Issuances.invoicedContract("4c2a3d8f-6b5e-4d9c-8f3a-2b1c4d5e6f7a",
            Tasks.INSURED, "КМ-000002");

    private final IssuanceRequestRepository requests = mock();
    private final ContractRepository contracts = mock();
    private final LmPolicyRepository policies = mock();
    private final PrintedFormRepository forms = mock();
    private final OrderInvoiceRepository invoices = mock();
    private final IssuanceSnapshots snapshots = new IssuanceSnapshots(requests, contracts, policies, forms, invoices);

    @Test
    void knownRequest_isSnapshottedWithItsCompositionCancellationAndRecords() {
        IssuanceRequest request = IssuanceRequest.start(Tasks.REQUEST, NOW);
        request.takeComposition(List.of(SPOUSE_KM, CHILD_LM), NOW);
        request.cancel(NOW);
        given(requests.findById(Tasks.REQUEST)).willReturn(Optional.of(request));
        given(contracts.findByRequestIndividualRequestId(Tasks.REQUEST)).willReturn(List.of(CONTRACT));
        given(policies.findByRequestIndividualRequestId(Tasks.REQUEST)).willReturn(List.of(POLICY));
        PrintedForm form = PrintedForms.issuedFor(PersonOwner.lmPolicy(POLICY.id()),
                new UskDocument(PrintedFormType.POLICY, "https://s3.sogaz.ru/rvl/lm/2026/02/0555123456.pdf", 96256L), NOW);
        given(forms.findByLmPolicyIdInOrContractIdInOrderByCreatedAtAscIdAsc(List.of(POLICY.id()), List.of(CONTRACT.id()))).willReturn(List.of(form));

        IssuanceSnapshot snapshot = snapshots.current(Tasks.REQUEST);

        assertThat(snapshot.individualRequestId()).isEqualTo(Tasks.REQUEST);
        assertThat(snapshot.formsOf(POLICY)).containsExactly(form);
        assertThat(snapshot.cancelled()).isTrue();
        assertThat(snapshot.composition()).containsExactly(SPOUSE_KM, CHILD_LM);
        assertThat(snapshot.contractOf(Tasks.SPOUSE)).isSameAs(CONTRACT);
        assertThat(snapshot.policyOf(Tasks.CHILD)).isSameAs(POLICY);
        assertThat(snapshot.statusOf(SPOUSE_KM)).contains(ContractStatus.NEW);
        assertThat(snapshot.statusOf(CHILD_LM)).contains(LmPolicyStatus.NEW);
    }

    @Test
    void invoicesOfTheRequestsContracts_areReadByTheirIdentifiersAndGiveThePaymentOffer() {
        given(requests.findById(Tasks.REQUEST)).willReturn(Optional.empty());
        given(contracts.findByRequestIndividualRequestId(Tasks.REQUEST)).willReturn(List.of(CONTRACT, INVOICED));
        given(policies.findByRequestIndividualRequestId(Tasks.REQUEST)).willReturn(List.of(POLICY));
        given(invoices.findByContractIdIn(List.of(CONTRACT.id(), INVOICED.id())))
                .willReturn(List.of(Invoices.issued(INVOICED.id())));

        IssuanceSnapshot snapshot = snapshots.current(Tasks.REQUEST);

        assertThat(snapshot.paymentOf(INVOICED)).isEqualTo(
                new PaymentOffer(false, Optional.of(Invoices.PAYMENT_URL), Optional.of(Invoices.DEADLINE)));
        assertThat(snapshot.paymentOf(CONTRACT))
                .isEqualTo(new PaymentOffer(false, Optional.empty(), Optional.empty()));
        verify(forms).findByLmPolicyIdInOrContractIdInOrderByCreatedAtAscIdAsc(List.of(POLICY.id()),
                List.of(CONTRACT.id(), INVOICED.id()));
    }

    @Test
    void unknownRequest_isSnapshottedAsNotCancelledWithAnEmptyComposition() {
        given(requests.findById(Tasks.REQUEST)).willReturn(Optional.empty());
        given(contracts.findByRequestIndividualRequestId(Tasks.REQUEST)).willReturn(List.of());
        given(policies.findByRequestIndividualRequestId(Tasks.REQUEST)).willReturn(List.of());
        given(forms.findByLmPolicyIdInOrContractIdInOrderByCreatedAtAscIdAsc(List.of(), List.of())).willReturn(List.of());

        IssuanceSnapshot snapshot = snapshots.current(Tasks.REQUEST);

        assertThat(snapshot.cancelled()).isFalse();
        assertThat(snapshot.composition()).isEmpty();
        assertThat(snapshot.records()).isEmpty();
        verify(invoices).findByContractIdIn(List.of());
    }
}
