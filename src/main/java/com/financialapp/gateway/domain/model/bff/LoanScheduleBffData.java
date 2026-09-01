package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.InstallmentRow;
import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;

public record LoanScheduleBffData(
        Section<List<InstallmentRow>> installments
) {}
