package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.AccessToken;
import com.financialapp.gateway.domain.common.model.Principal;

public interface TokenVerificationGateway {
    Principal verify(AccessToken token);
}
