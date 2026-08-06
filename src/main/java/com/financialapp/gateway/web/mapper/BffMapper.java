package com.financialapp.gateway.web.mapper;

import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.web.dto.response.SectionResponse;

public final class BffMapper {

    private BffMapper() {}

    public static <T> SectionResponse<T> toSectionResponse(Section<T> section) {
        if (section == null) {
            return null;
        }
        return new SectionResponse<>(section.status().name(), section.observedAt().value(), section.data());
    }
}
