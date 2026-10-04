package de.coldtea.verborum.msuser.common.mapper;

import de.coldtea.verborum.msuser.user.dto.ProfileResponseDTO;
import de.coldtea.verborum.msuser.user.dto.UserRequestDTO;
import de.coldtea.verborum.msuser.user.dto.UserResponseDTO;
import de.coldtea.verborum.msuser.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponseDTO toUserResponseDTO(User user);

    // The marketplace agreement is not part of the full-profile request: saveUser copies it from the
    // stored profile (P4-14 rule 3, never clear by omission)
    @Mapping(target = "marketplaceAgreementAccepted", ignore = true)
    @Mapping(target = "marketplaceAgreementVersion", ignore = true)
    @Mapping(target = "marketplaceAgreementAcceptedAt", ignore = true)
    User toUser(UserRequestDTO userRequestDTO);

    @Mapping(source = "userId", target = "id")
    ProfileResponseDTO toProfileResponseDTO(User user);
}
