package com.banking.handoff.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.banking.handoff.domain.FieldDefinition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@ConfigurationProperties(prefix = "handoff")
@Validated
public class FeedProperties {

    @Valid @NotNull
    private Output output = new Output();

    @Valid @NotNull
    private Batch batch = new Batch();

    @Valid @NotNull
    private StagingConfig instrumentStaging = new StagingConfig();

    @Valid @NotNull
    private StagingConfig accountingStaging = new StagingConfig();

    @Valid @NotNull
    private FeedConfig instrument = new FeedConfig();

    @Valid @NotNull
    private FeedConfig accounting = new FeedConfig();

    public Output getOutput() { return output; }
    public void setOutput(Output output) { this.output = output; }

    public Batch getBatch() { return batch; }
    public void setBatch(Batch batch) { this.batch = batch; }

    public StagingConfig getInstrumentStaging() { return instrumentStaging; }
    public void setInstrumentStaging(StagingConfig instrumentStaging) { this.instrumentStaging = instrumentStaging; }

    public StagingConfig getAccountingStaging() { return accountingStaging; }
    public void setAccountingStaging(StagingConfig accountingStaging) { this.accountingStaging = accountingStaging; }

    public FeedConfig getInstrument() { return instrument; }
    public void setInstrument(FeedConfig instrument) { this.instrument = instrument; }

    public FeedConfig getAccounting() { return accounting; }
    public void setAccounting(FeedConfig accounting) { this.accounting = accounting; }

    // ── Format ────────────────────────────────────────────────────────────────

    public enum Format { CSV, FIXED_WIDTH }

    // ── Output ───────────────────────────────────────────────────────────────

    public static class Output {

        @NotBlank
        private String directory;

        private String encoding = "UTF-8";

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }

        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
    }

    // ── Batch ────────────────────────────────────────────────────────────────

    public static class Batch {

        @Positive private int chunkSize = 1000;
        @Positive private int pageSize = 1000;
        @Positive private int skipLimit = 10;

        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }

        public int getSkipLimit() { return skipLimit; }
        public void setSkipLimit(int skipLimit) { this.skipLimit = skipLimit; }
    }

    // ── Staging ──────────────────────────────────────────────────────────────

    public static class StagingConfig {

        @NotBlank
        private String tableName;

        @Valid @NotNull
        private DatasourceConfig source = new DatasourceConfig();

        @NotEmpty
        private List<String> columns;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public DatasourceConfig getSource() { return source; }
        public void setSource(DatasourceConfig source) { this.source = source; }

        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
    }

    // ── Per-feed config ──────────────────────────────────────────────────────

    public static class FeedConfig {

        private boolean enabled = true;

        private Format format = Format.FIXED_WIDTH;

        @Valid @NotNull
        private FeedOutput output = new FeedOutput();

        @NotEmpty
        private List<@Valid FieldDefinition> fields;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Format getFormat() { return format; }
        public void setFormat(Format format) { this.format = format; }

        public FeedOutput getOutput() { return output; }
        public void setOutput(FeedOutput output) { this.output = output; }

        public List<FieldDefinition> getFields() { return fields; }
        public void setFields(List<FieldDefinition> fields) { this.fields = fields; }
    }

    public static class FeedOutput {

        private String filePrefix = "FEED_";
        private String fileSuffix = ".dat";

        public String getFilePrefix() { return filePrefix; }
        public void setFilePrefix(String filePrefix) { this.filePrefix = filePrefix; }

        public String getFileSuffix() { return fileSuffix; }
        public void setFileSuffix(String fileSuffix) { this.fileSuffix = fileSuffix; }
    }

    // ── Shared datasource config ─────────────────────────────────────────────

    public static class DatasourceConfig {

        @NotBlank private String selectClause;
        @NotBlank private String fromClause;
        private String whereClause;
        @NotBlank private String sortKey;

        public String getSelectClause() { return selectClause; }
        public void setSelectClause(String selectClause) { this.selectClause = selectClause; }

        public String getFromClause() { return fromClause; }
        public void setFromClause(String fromClause) { this.fromClause = fromClause; }

        public String getWhereClause() { return whereClause; }
        public void setWhereClause(String whereClause) { this.whereClause = whereClause; }

        public String getSortKey() { return sortKey; }
        public void setSortKey(String sortKey) { this.sortKey = sortKey; }
    }
}
