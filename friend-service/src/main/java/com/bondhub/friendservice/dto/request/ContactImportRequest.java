package com.bondhub.friendservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ContactImportRequest(
    @NotNull(message = "Contacts list cannot be null")
    @Size(min = 1, max = 500, message = "Contacts list must have between 1 and 500 entries")
    List<@Valid ContactEntry> contacts
) {
    public record ContactEntry(
        String name,
        List<String> phones,
        List<String> emails
    ) {}
}
