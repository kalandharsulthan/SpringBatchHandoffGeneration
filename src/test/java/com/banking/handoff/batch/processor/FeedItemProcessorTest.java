package com.banking.handoff.batch.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banking.handoff.domain.FieldDefinition;
import com.banking.handoff.domain.FieldDefinition.Alignment;
import com.banking.handoff.domain.HandoffRecord;
import com.banking.handoff.util.FixedWidthFormatter;

@ExtendWith(MockitoExtension.class)
class FeedItemProcessorTest {

    @Mock
    private FixedWidthFormatter formatter;

    private FeedItemProcessor processor;
    private List<FieldDefinition> fields;

    @BeforeEach
    void setUp() {
        fields = new ArrayList<>();
        fields.add(makeField("account_no", 20));
        fields.add(makeField("customer_name", 40));
        fields.add(makeField("balance", 15));
        processor = new FeedItemProcessor(fields, formatter);
    }

    @Test
    void shouldMapAllFieldsInOrder() throws Exception {
        when(formatter.format(eq("ACC001"), any())).thenReturn("ACC001              ");
        when(formatter.format(eq("John Smith"), any())).thenReturn("John Smith                              ");
        when(formatter.format(eq("1234.50"), any())).thenReturn("000000001234.50");

        Map<String, Object> row = new HashMap<>();
        row.put("account_no", "ACC001");
        row.put("customer_name", "John Smith");
        row.put("balance", "1234.50");

        HandoffRecord record = processor.process(row);

        assertThat(record.getFields()).containsKeys("account_no", "customer_name", "balance");
        List<String> keys = new ArrayList<>(record.getFields().keySet());
        assertThat(keys).containsExactly("account_no", "customer_name", "balance");
    }

    @Test
    void shouldHandleNullValueInRow() throws Exception {
        when(formatter.format(eq(null), any())).thenReturn("                    ");
        when(formatter.format(eq("Jane Doe"), any())).thenReturn("Jane Doe                                ");
        when(formatter.format(eq("0.00"), any())).thenReturn("000000000000.00");

        Map<String, Object> row = new HashMap<>();
        row.put("account_no", null);
        row.put("customer_name", "Jane Doe");
        row.put("balance", "0.00");

        HandoffRecord record = processor.process(row);

        assertThat(record.getField("account_no")).isEqualTo("                    ");
    }

    @Test
    void shouldHandleMissingKeyInRow() throws Exception {
        when(formatter.format(any(), any())).thenReturn("X".repeat(15));

        Map<String, Object> row = new HashMap<>();
        row.put("customer_name", "Test");

        HandoffRecord record = processor.process(row);

        assertThat(record.getFields()).hasSize(3);
    }

    @Test
    void shouldPreserveFieldInsertionOrder() throws Exception {
        when(formatter.format(any(), any())).thenReturn("VALUE");

        Map<String, Object> row = Map.of(
                "account_no", "A",
                "customer_name", "B",
                "balance", "C");

        HandoffRecord record = processor.process(row);

        List<String> keyOrder = new ArrayList<>(record.getFields().keySet());
        assertThat(keyOrder).containsExactly("account_no", "customer_name", "balance");
    }

    private FieldDefinition makeField(String name, int length) {
        FieldDefinition f = new FieldDefinition();
        f.setName(name);
        f.setLength(length);
        f.setAlignment(Alignment.LEFT);
        f.setPadChar(' ');
        return f;
    }
}
