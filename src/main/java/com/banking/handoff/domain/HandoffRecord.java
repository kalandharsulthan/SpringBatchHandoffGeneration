package com.banking.handoff.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class HandoffRecord {

    private final Map<String, String> fields;

    public HandoffRecord() {
        this.fields = new LinkedHashMap<>();
    }

    public HandoffRecord(Map<String, String> fields) {
        this.fields = new LinkedHashMap<>(fields);
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void putField(String name, String value) {
        fields.put(name, value);
    }

    public String getField(String name) {
        return fields.get(name);
    }
}
