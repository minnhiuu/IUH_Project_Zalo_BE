package com.bondhub.messageservice.dto.request;

public record UpdateGroupSettingsRequest(
        Boolean memberCanChangeInfo,
        Boolean memberCanPinMessages,
        Boolean memberCanCreateNotes,
        Boolean memberCanCreatePolls,
        Boolean memberCanSendMessages,
        Boolean membershipApprovalEnabled,
        Boolean highlightAdminMessages,
        Boolean newMembersCanReadRecent,
        Boolean joinByLinkEnabled
) {}
