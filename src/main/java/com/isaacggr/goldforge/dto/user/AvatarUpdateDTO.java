package com.isaacggr.goldforge.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para atualização do avatar RPG do usuário")
public record AvatarUpdateDTO(
        @Schema(description = "Identificador do avatar selecionado", example = "avatar-03")
        @NotBlank(message = "O avatarId é obrigatório")
        @Size(max = 50, message = "O identificador do avatar deve ter no máximo 50 caracteres")
        String avatarId
) {}
