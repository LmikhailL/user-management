package org.mike.usermanagement.user.web;

import org.mapstruct.Mapper;
import org.mike.usermanagement.user.domain.RegisterUserCommand;
import org.mike.usermanagement.user.domain.RegisteredUser;
import org.mike.usermanagement.web.generated.model.RegisterUserRequest;
import org.mike.usermanagement.web.generated.model.RegisteredUserResponse;

@Mapper(componentModel = "spring")
public interface RegisterUserWebMapper {

    RegisterUserCommand toCommand(RegisterUserRequest request);

    RegisteredUserResponse toResponse(RegisteredUser registeredUser);
}
