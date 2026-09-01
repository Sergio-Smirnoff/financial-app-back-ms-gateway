package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;
import com.financialapp.gateway.web.dto.response.bff.BffWebResponses.*;

import java.util.List;

public record SettingsBffResponse(
        SectionResponse<UserProfileResponse> profile,
        SectionResponse<UserPreferencesResponse> preferences,
        SectionResponse<FeesSummaryResponse> fees,
        SectionResponse<List<NotificationPreferenceResponse>> notificationPrefs,
        SectionResponse<List<SessionRowResponse>> sessions
) {}
