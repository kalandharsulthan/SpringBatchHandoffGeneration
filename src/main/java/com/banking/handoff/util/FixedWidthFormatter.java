package com.banking.handoff.util;

import org.springframework.stereotype.Component;

import com.banking.handoff.domain.FieldDefinition;
import com.banking.handoff.domain.FieldDefinition.Alignment;

@Component
public class FixedWidthFormatter {

    public String format(Object rawValue, FieldDefinition field) {
        String value = applyFormat(rawValue, field);

        if (value.length() > field.getLength()) {
            value = value.substring(0, field.getLength());
        }

        return pad(value, field);
    }

    private String applyFormat(Object rawValue, FieldDefinition field) {
        if (rawValue == null) {
            return "";
        }
        if (field.getFormat() != null && !field.getFormat().isBlank()) {
            return String.format(field.getFormat(), rawValue);
        }
        return rawValue.toString();
    }

    private String pad(String value, FieldDefinition field) {
        int deficit = field.getLength() - value.length();
        if (deficit == 0) {
            return value;
        }
        String padding = String.valueOf(field.getPadChar()).repeat(deficit);
        if (field.getAlignment() == Alignment.LEFT) {
            return value + padding;
        }
        return padding + value;
    }
}
