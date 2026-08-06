package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.composition.Section;

public record TransactionDetailBffData(
        Section<TransactionDetailData> detail
) {}
