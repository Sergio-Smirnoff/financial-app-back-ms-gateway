package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.AccountOptionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.LoanDetailRowResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.LoansKpisResponse;

import java.util.List;

public record LoansBffResponse(
        SectionResponse<LoansKpisResponse> kpis,
        SectionResponse<List<LoanDetailRowResponse>> loans,
        SectionResponse<List<AccountOptionResponse>> payFromAccounts
) {}
