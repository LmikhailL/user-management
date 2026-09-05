package org.mike.usermanagement.user.web;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mike.usermanagement.user.domain.RegisterUserCommand;
import org.mike.usermanagement.user.domain.RegistrationResult;
import org.mike.usermanagement.web.generated.model.RegisterUserRequest;
import org.mike.usermanagement.web.generated.model.RegisteredUserResponse;

@Mapper(componentModel = "spring")
public interface RegisterUserWebMapper {

    RegisterUserCommand toCommand(RegisterUserRequest request);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "email", source = "user.email")
    RegisteredUserResponse toResponse(RegistrationResult registrationResult);
}
