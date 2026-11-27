package ru.projectx.contracts.service.packages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.dto.PackageItem;
import ru.projectx.contracts.dto.PackageItem.PackageAcceptance;
import ru.projectx.contracts.dto.PackageItem.PackageDocument;
import ru.projectx.contracts.dto.PackageItem.PackagePayment;
import ru.projectx.contracts.dto.PackageItem.PaymentState;
import ru.projectx.contracts.dto.PackageResponse;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStep;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedForms;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.document.UskDocument;
import ru.projectx.contracts.exception.RequestNotFoundException;
import ru.projectx.contracts.repository.issuance.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.IssuanceSnapshots;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class RequestPackagesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final String CONTRACT_ID = "3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4";
    private static final UUID FORM_ID = UUID.fromString("c2e77a10-6b3d-4a02-8e19-5d4402fa77bb");
    private static final String FORM_URL = "https://s3.example.test/lm/0555123456.pdf";
    private static final RequestParticipant INSURED = new RequestParticipant(Tasks.INSURED, DocumentKind.NONE);
    private static final RequestParticipant SPOUSE = new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT);
    private static final RequestParticipant CHILD = new RequestParticipant(Tasks.CHILD, DocumentKind.LM_POLICY);

    private final IssuanceRequestRepository requests = mock();
    private final IssuanceSnapshots snapshots = mock();
    private final ContractAcceptanceRepository acceptances = mock();
    private final RequestPackages packages = new RequestPackages(requests, snapshots, acceptances);
    private final Contract contract = Issuances.contract(CONTRACT_ID, Tasks.SPOUSE, "DKM-2026-000001");
    private final LmPolicy policy = Issuances.policy("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39", Tasks.CHILD);

    @BeforeEach
    void requestIsKnown() {
        given(requests.existsById(Tasks.REQUEST)).willReturn(true);
        given(acceptances.findByContractIdIn(anyCollection())).willReturn(List.of());
        contract.offerDraft();
    }

    @Test
    void package_listsTheLastCompositionInOrderWithTheStatusOfTheRequest() {
        snapshot(List.of(INSURED, SPOUSE, CHILD), List.of());

        PackageResponse response = packages.packageOf(Tasks.REQUEST);

        assertThat(response.individualRequestId()).isEqualTo(Tasks.REQUEST);
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.items()).extracting(PackageItem::participantId)
                .containsExactly(Tasks.INSURED, Tasks.SPOUSE, Tasks.CHILD);
        assertThat(response.items().get(0)).isEqualTo(new PackageItem(Tasks.INSURED, DocumentKind.NONE, null, null, null,
                DocumentState.NOT_REQUIRED, null, null, null, null, List.of(), null, null));
        verify(acceptances).findByContractIdIn(List.of(UUID.fromString(CONTRACT_ID)));
    }

    @Test
    void contractReadyForAcceptance_isDraftedWithNumberVersionAndPremiumWithoutAcceptanceOrPayment() {
        contract.advanceVersion(NOW);
        snapshot(List.of(SPOUSE), List.of());

        assertThat(packages.packageOf(Tasks.REQUEST).items()).containsExactly(new PackageItem(Tasks.SPOUSE,
                DocumentKind.KM_CONTRACT, UUID.fromString(CONTRACT_ID), "DKM-2026-000001", 2, DocumentState.DRAFTED,
                new BigDecimal("34000.00"), null, null, null, List.of(), null, null));
    }

    @Test
    void acceptedContract_carriesTheAcceptanceAndAPendingPaymentOfThePremiumWithoutLink() {
        contract.advance(ContractStep.OFFER_ACCEPTED);
        given(acceptances.findByContractIdIn(anyCollection())).willReturn(List.of(ContractAcceptance.record(
                contract.id(), new OfferAcceptance(1, Tasks.INSURED, Instant.parse("2026-02-10T07:04:58Z"),
                        AcceptanceChannel.WEB, null, null), NOW)));
        snapshot(List.of(SPOUSE), List.of());

        PackageItem item = packages.packageOf(Tasks.REQUEST).items().get(0);

        assertThat(item.state()).isEqualTo(DocumentState.ACCEPTED);
        assertThat(item.acceptance()).isEqualTo(new PackageAcceptance(Instant.parse("2026-02-10T07:04:58Z"), 1));
        assertThat(item.payment()).isEqualTo(new PackagePayment(PaymentState.PENDING, new BigDecimal("34000.00"), null,
                null));
    }

    @Test
    void contractInError_carriesTheCodeAndTextWithoutPayment() {
        contract.markError(new IssueError("RELATION_TYPE_REQUIRED", "поле обязательно"), NOW);
        snapshot(List.of(SPOUSE), List.of());

        PackageItem item = packages.packageOf(Tasks.REQUEST).items().get(0);

        assertThat(item.state()).isEqualTo(DocumentState.ERROR);
        assertThat(item.errorCode()).isEqualTo("RELATION_TYPE_REQUIRED");
        assertThat(item.errorMessage()).isEqualTo("поле обязательно");
        assertThat(item.payment()).isNull();
        assertThat(item.contractNumber()).isEqualTo("DKM-2026-000001");
    }

    @Test
    void policyInWork_isNotRequiredWithoutNumberOrDocuments() {
        policy.submit(NOW);
        snapshot(List.of(CHILD), List.of());

        assertThat(packages.packageOf(Tasks.REQUEST).items()).containsExactly(new PackageItem(Tasks.CHILD,
                DocumentKind.LM_POLICY, null, null, null, DocumentState.NOT_REQUIRED, null, null, null, null, List.of(),
                null, null));
    }

    @Test
    void issuedPolicy_carriesItsNumberAndTheFinalFormFromTheUsk() {
        policy.submit(NOW);
        policy.markIssued(new IssuedPolicy(UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012"), "LM-000012204",
                UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0"), "0555123456"), NOW);
        PrintedForm form = PrintedForms.issuedFor(PersonOwner.lmPolicy(policy.id()),
                new UskDocument(PrintedFormType.POLICY, FORM_URL, 96256L), NOW);
        ReflectionTestUtils.setField(form, "id", FORM_ID);
        snapshot(List.of(CHILD), List.of(form));

        PackageItem item = packages.packageOf(Tasks.REQUEST).items().get(0);

        assertThat(item.state()).isEqualTo(DocumentState.ISSUED);
        assertThat(item.policyNumber()).isEqualTo("0555123456");
        assertThat(item.documents()).containsExactly(new PackageDocument(FORM_ID, "POLICY", "FINAL", 1, FORM_URL));
    }

    @Test
    void policyInError_carriesTheCodeAndText() {
        policy.markError(new IssueError("PROGRAM_REQUIRED", "поле обязательно"), NOW);
        snapshot(List.of(CHILD), List.of());

        PackageItem item = packages.packageOf(Tasks.REQUEST).items().get(0);

        assertThat(item.state()).isEqualTo(DocumentState.ERROR);
        assertThat(item.errorCode()).isEqualTo("PROGRAM_REQUIRED");
        assertThat(item.errorMessage()).isEqualTo("поле обязательно");
    }

    @Test
    void unknownRequest_isNotFoundWithoutReadingTheSnapshot() {
        given(requests.existsById(Tasks.REQUEST)).willReturn(false);

        assertThatThrownBy(() -> packages.packageOf(Tasks.REQUEST)).isInstanceOf(RequestNotFoundException.class);
        verifyNoInteractions(snapshots, acceptances);
    }

    private void snapshot(List<RequestParticipant> composition, List<PrintedForm> forms) {
        given(snapshots.current(Tasks.REQUEST)).willReturn(new IssuanceSnapshot(Tasks.REQUEST, false, composition,
                List.of(contract), List.of(policy), forms));
    }
}
