package com.bondhub.friendservice.service.contact;

import com.bondhub.friendservice.dto.request.ContactImportRequest;
import com.bondhub.friendservice.dto.response.ContactImportResponse;

public interface ContactService {

    ContactImportResponse importContacts(ContactImportRequest request);
}
