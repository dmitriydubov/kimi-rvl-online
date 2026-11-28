package ru.projectx.contracts.entity.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public record ParticipantAttachment(
        @Column(name = "individual_request_id", updatable = false) UUID individualRequestId,
        @Column(name = "participant_id", updatable = false) UUID participantId,
        @Column(name = "policy_id", updatable = false) UUID policyId,
        @Column(name = "family_member_id", updatable = false) UUID familyMemberId,
        @Column(name = "attachment_id", updatable = false) UUID attachmentId) {
}
