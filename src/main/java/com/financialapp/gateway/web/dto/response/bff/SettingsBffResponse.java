package com.financialapp.gateway.web.dto.response.bff;

import com.financialapp.gateway.web.dto.response.SectionResponse;

import java.util.List;
import java.util.Map;

public record SettingsBffResponse(
        SectionResponse<Map<String, Object>> profile,
        SectionResponse<Map<String, Object>> preferences,
        SectionResponse<Map<String, Object>> fees,
        SectionResponse<List<Map<String, Object>>> notificationPrefs,
        SectionResponse<List<Map<String, Object>>> sessions
) {}
