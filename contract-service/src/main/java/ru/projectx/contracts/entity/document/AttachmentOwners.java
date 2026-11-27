package ru.projectx.contracts.entity.document;

import java.util.UUID;

public final class AttachmentOwners {

    private AttachmentOwners() {
    }

    public static ParticipantAttachment certificate(UUID request, UUID participant, UUID attachment) {
        return new ParticipantAttachment(request, participant, null, null, attachment);
    }

    public static ParticipantAttachment consent(UUID policy, UUID familyMember, UUID attachment) {
        return new ParticipantAttachment(null, null, policy, familyMember, attachment);
    }
}
