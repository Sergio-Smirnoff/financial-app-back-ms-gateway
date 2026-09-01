package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.*;

import java.util.List;

public record ImportsBffResponse(
        SectionResponse<ActiveRunResponse> activeRun,
        SectionResponse<List<ImportRunRowResponse>> history,
        SectionResponse<List<ReconciliationRowResponse>> reconciliation
) {}
