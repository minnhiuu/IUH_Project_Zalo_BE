package com.bondhub.authservice.mapper;

import com.bondhub.authservice.dto.account.request.AccountCreateRequest;
import com.bondhub.authservice.dto.account.response.AccountResponse;
import com.bondhub.authservice.dto.account.request.AccountUpdateRequest;
import com.bondhub.authservice.model.Account;
import com.bondhub.common.enums.Role;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper interface for converting between Account entities and DTOs.
 * <p>
 * This interface uses MapStruct to map Account entities to response DTOs
 * and request DTOs to Account entities.
 * </p>
 *
 * @author BondHub Development Team
 * @version 1.0
 * @since 2026-01-15
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountMapper {

    @Mapping(source = "role", target = "role", qualifiedByName = "roleToString")
    AccountResponse toResponse(Account account);

    Account toEntity(AccountCreateRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(@MappingTarget Account account, AccountUpdateRequest request);

    @Named("roleToString")
    default String roleToString(Role role) {
        return role != null ? role.name() : null;
    }
}
