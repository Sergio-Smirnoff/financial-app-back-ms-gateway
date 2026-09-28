package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.model.bff.BffDomainModels.CategoryOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CategoryTree {

    private static final String SOURCE = "ms-finances categories";

    private CategoryTree() {
    }

    static String childLabel(String parent, String child) {
        return parent + " / " + child;
    }

    static List<CategoryOption> options(List<Map<String, Object>> categories) {
        List<CategoryOption> options = new ArrayList<>();
        for (DownstreamPayload category : DownstreamPayload.rows(SOURCE, categories)) {
            String name = category.text("name");
            options.add(new CategoryOption(category.longValue("id"), name));
            for (DownstreamPayload child : category.listOrEmpty("subcategories")) {
                options.add(new CategoryOption(child.longValue("id"), childLabel(name, child.text("name"))));
            }
        }
        return List.copyOf(options);
    }
}
