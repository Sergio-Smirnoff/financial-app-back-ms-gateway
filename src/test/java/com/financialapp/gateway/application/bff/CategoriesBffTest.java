package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetCategoriesBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.CategoriesBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriesBffTest {

    @Mock private FinancesGateway finances;
    @Mock private InvestmentsGateway investments;

    private GetCategoriesBffUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetCategoriesBffUseCaseImpl(finances, investments, PageTimeoutBudget.fromMillis(3000));
    }

    @Test
    void execute_returnsOkSectionsForCategories() {
        when(finances.fetchBudgetPace(any(), any())).thenReturn(CompletableFuture.completedFuture(Map.of("pace", "OK")));
        when(finances.fetchBudgets(any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchMonthlyFlow(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchCategorizationRules(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        CategoriesBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.kpis().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.budgets().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.rules().status()).isEqualTo(SectionStatus.OK);
    }
}
