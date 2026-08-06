package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetImportsBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.ImportsBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
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
class ImportsBffTest {

    @Mock private UploadGateway upload;

    private GetImportsBffUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetImportsBffUseCaseImpl(upload, PageTimeoutBudget.fromMillis(3000));
    }

    @Test
    void execute_returnsOkSectionsForImports() {
        when(upload.fetchHistory(any())).thenReturn(CompletableFuture.completedFuture(List.of(Map.of("id", 1L))));

        ImportsBffData data = useCase.execute(new UserId(1L)).join();

        assertThat(data.activeRun().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.history().status()).isEqualTo(SectionStatus.OK);
    }
}
