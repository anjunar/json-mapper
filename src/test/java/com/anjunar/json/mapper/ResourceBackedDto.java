package com.anjunar.json.mapper;

import com.anjunar.json.mapper.annotations.JsonLdId;
import com.anjunar.json.mapper.annotations.JsonLdResource;
import com.anjunar.json.mapper.provider.DTO;
import jakarta.json.bind.annotation.JsonbProperty;

import java.util.UUID;

@JsonLdResource("https://technologyspeaks.com/service/core/users/user/")
public class ResourceBackedDto implements DTO {

    @JsonbProperty("@id")
    @JsonLdId
    public UUID id;

    @JsonbProperty
    public String name;
}
