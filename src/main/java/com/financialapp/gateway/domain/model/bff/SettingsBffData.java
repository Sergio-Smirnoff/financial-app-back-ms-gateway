package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;

public record SettingsBffData(
        Section<UserProfile> profile,
        Section<UserPreferences> preferences,
        Section<FeesSummary> fees,
        Section<List<NotificationPreference>> notificationPrefs,
        Section<List<SessionRow>> sessions
) {}
