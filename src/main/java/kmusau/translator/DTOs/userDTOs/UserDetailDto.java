package kmusau.translator.DTOs.userDTOs;

import kmusau.translator.entity.UsersEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailDto {

    private Long userId;

    private String username;

    public UsersEntity fromDto(UserDetailDto dto) {
        UsersEntity user = new UsersEntity();
        if (dto == null)
            return user;
        if (dto.getUsername() != null)
            user.setUsername(dto.getUsername());
        if (dto.getUserId() != null)
            user.setUserId(dto.getUserId());


        return user;

    }

    public UserDetailDto toDto(UsersEntity usersEntity) {
        UserDetailDto userDto = new UserDetailDto();
        if (usersEntity == null)
            return userDto;

        if (usersEntity.getUserId() != null)
            userDto.setUserId(usersEntity.getUserId());
        if (usersEntity.getUsername() != null)
            userDto.setUsername(usersEntity.getUsername());

        return userDto;
    }
}
