package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.*;

public record TransactionsBffResponse(
        SectionResponse<TransactionsSummaryResponse> summary,
        SectionResponse<TransactionsPageResponse> page,
        SectionResponse<FilterOptionsResponse> filterOptions,
        SectionResponse<UncategorisedSummaryResponse> uncategorised
) {}
