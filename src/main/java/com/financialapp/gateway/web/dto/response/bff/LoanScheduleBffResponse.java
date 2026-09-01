package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.InstallmentRowResponse;

import java.util.List;

public record LoanScheduleBffResponse(
        SectionResponse<List<InstallmentRowResponse>> installments
) {}
