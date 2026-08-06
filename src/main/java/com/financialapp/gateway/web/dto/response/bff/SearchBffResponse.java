package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;

import java.util.List;
import java.util.Map;

public record SearchBffResponse(
        SectionResponse<List<Map<String, Object>>> movements,
        SectionResponse<List<Map<String, Object>>> positions,
        SectionResponse<List<Map<String, Object>>> categories
) {}
