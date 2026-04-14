package com.bondhub.messageservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupSettings {

    @Builder.Default
    private boolean memberCanChangeInfo = true;

    @Builder.Default
    private boolean memberCanPinMessages = true;

    @Builder.Default
    private boolean memberCanCreateNotes = true;

    @Builder.Default
    private boolean memberCanCreatePolls = true;

    @Builder.Default
    private boolean memberCanSendMessages = true;

    @Builder.Default
    private boolean membershipApprovalEnabled = false;

    @Builder.Default
    private boolean highlightAdminMessages = true;

    @Builder.Default
    private boolean newMembersCanReadRecent = true;

    @Builder.Default
    private boolean joinByLinkEnabled = false;

    private String joinQuestion;
}
