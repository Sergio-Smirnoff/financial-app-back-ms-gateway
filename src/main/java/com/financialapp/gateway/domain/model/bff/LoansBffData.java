package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.AccountOption;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.LoanDetailRow;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.LoansKpis;
import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;

public record LoansBffData(
        Section<LoansKpis> kpis,
        Section<List<LoanDetailRow>> loans,
        Section<List<AccountOption>> payFromAccounts
) {}
