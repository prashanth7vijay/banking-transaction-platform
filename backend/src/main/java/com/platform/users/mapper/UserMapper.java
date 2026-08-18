package com.platform.users.mapper;

import com.platform.users.domain.Role;
import com.platform.users.domain.User;
import com.platform.users.dto.UserResponse;
import org.mapstruct.Mapper;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    default UserResponse toResponse(User user) {
        if (user == null) return null;
        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                roleNames,
                user.getCreatedAt()
        );
    }
}
