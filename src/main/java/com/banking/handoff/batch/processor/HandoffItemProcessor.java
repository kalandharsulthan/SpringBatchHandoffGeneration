package com.banking.handoff.batch.processor;

import java.util.Map;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.banking.handoff.config.HandoffProperties;
import com.banking.handoff.domain.FieldDefinition;
import com.banking.handoff.domain.HandoffRecord;
import com.banking.handoff.util.FixedWidthFormatter;

@Component
public class HandoffItemProcessor implements ItemProcessor<Map<String, Object>, HandoffRecord> {

    private final HandoffProperties properties;
    private final FixedWidthFormatter formatter;

    public HandoffItemProcessor(HandoffProperties properties, FixedWidthFormatter formatter) {
        this.properties = properties;
        this.formatter = formatter;
    }

    @Override
    public HandoffRecord process(Map<String, Object> row) {
        HandoffRecord record = new HandoffRecord();
        for (FieldDefinition field : properties.getFields()) {
            Object rawValue = row.get(field.getName());
            String formatted = formatter.format(rawValue, field);
            record.putField(field.getName(), formatted);
        }
        return record;
    }
}
