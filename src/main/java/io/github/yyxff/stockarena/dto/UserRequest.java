package io.github.yyxff.stockarena.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class UserRequest {
    private String username;
    private String password;
}
