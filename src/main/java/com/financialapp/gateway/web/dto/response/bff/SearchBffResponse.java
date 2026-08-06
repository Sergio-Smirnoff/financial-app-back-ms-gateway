package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.*;

import java.util.List;

public record SearchBffResponse(
        SectionResponse<List<SearchHitResponse>> movements,
        SectionResponse<List<SearchHitResponse>> positions,
        SectionResponse<List<SearchHitResponse>> categories
) {}
