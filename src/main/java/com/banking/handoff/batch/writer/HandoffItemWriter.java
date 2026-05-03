package com.banking.handoff.batch.writer;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;
import com.banking.handoff.domain.HandoffRecord;

/**
 * Delegating writer that wraps FlatFileItemWriter and tracks record count.
 * The actual FlatFileItemWriter bean is configured in BatchJobConfig with @StepScope.
 */
public class HandoffItemWriter implements ItemWriter<HandoffRecord>, ItemStream {

    private FlatFileItemWriter<HandoffRecord> delegate;
    private long recordCount = 0;

    public void setDelegate(FlatFileItemWriter<HandoffRecord> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(Chunk<? extends HandoffRecord> chunk) throws Exception {
        delegate.write(chunk);
        recordCount += chunk.size();
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        delegate.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        delegate.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        delegate.close();
    }

    public long getRecordCount() {
        return recordCount;
    }
}
