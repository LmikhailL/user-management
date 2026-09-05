package org.mike.usermanagement.verification.web;

import org.mapstruct.Mapper;
import org.mike.usermanagement.user.domain.RegisteredUser;
import org.mike.usermanagement.web.generated.model.VerifyEmailResponse;

@Mapper(componentModel = "spring")
public interface VerifyEmailWebMapper {

    VerifyEmailResponse toResponse(RegisteredUser registeredUser);
}
