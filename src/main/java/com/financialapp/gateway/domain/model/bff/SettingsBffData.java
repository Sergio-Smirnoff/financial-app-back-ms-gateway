package com.financialapp.gateway.domain.model.bff;

import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;
import java.util.Map;

public record SettingsBffData(
        Section<Map<String, Object>> profile,
        Section<Map<String, Object>> preferences,
        Section<Map<String, Object>> fees,
        Section<List<Map<String, Object>>> notificationPrefs,
        Section<List<Map<String, Object>>> sessions
) {}
