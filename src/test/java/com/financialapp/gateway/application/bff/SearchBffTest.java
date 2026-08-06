package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetSearchBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.SearchBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchBffTest {

    @Mock private FinancesGateway finances;
    @Mock private InvestmentsGateway investments;

    private GetSearchBffUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSearchBffUseCaseImpl(finances, investments, PageTimeoutBudget.fromMillis(3000));
    }

    @Test
    void execute_returnsOkGroupedSearchSections() {
        when(finances.searchTransactions(any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.searchPositions(any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchCategorizationRules(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        SearchBffData data = useCase.execute(new UserId(1L), "groceries").join();

        assertThat(data.movements().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.positions().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.categories().status()).isEqualTo(SectionStatus.OK);
    }
}
