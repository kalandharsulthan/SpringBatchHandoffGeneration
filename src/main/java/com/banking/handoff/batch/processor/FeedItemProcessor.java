package com.banking.handoff.batch.processor;

import java.util.List;
import java.util.Map;

import org.springframework.batch.item.ItemProcessor;

import com.banking.handoff.domain.FieldDefinition;
import com.banking.handoff.domain.HandoffRecord;
import com.banking.handoff.util.FixedWidthFormatter;

public class FeedItemProcessor implements ItemProcessor<Map<String, Object>, HandoffRecord> {

    private final List<FieldDefinition> fields;
    private final FixedWidthFormatter formatter;

    public FeedItemProcessor(List<FieldDefinition> fields, FixedWidthFormatter formatter) {
        this.fields = fields;
        this.formatter = formatter;
    }

    @Override
    public HandoffRecord process(Map<String, Object> row) {
        HandoffRecord record = new HandoffRecord();
        for (FieldDefinition field : fields) {
            Object rawValue = row.get(field.getName());
            String formatted = formatter.format(rawValue, field);
            record.putField(field.getName(), formatted);
        }
        return record;
    }
}
