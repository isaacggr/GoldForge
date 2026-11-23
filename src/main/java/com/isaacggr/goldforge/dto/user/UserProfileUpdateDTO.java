package com.isaacggr.goldforge.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados atualizáveis do perfil do usuário autenticado")
public record UserProfileUpdateDTO(

        @Schema(description = "Nome completo atualizado do usuário", example = "Isaac Gregório")
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 2, max = 50, message = "O nome deve ter entre 2 e 50 caracteres")
        String name,

        @Schema(description = "Identificador do avatar pixel art RPG", example = "avatar-03")
        @Size(max = 50, message = "O identificador do avatar deve ter no máximo 50 caracteres")
        String avatarId
) {
    public UserProfileUpdateDTO(String name) {
        this(name, null);
    }
}
