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
public class HandoffProperties {

    @Valid
    @NotNull
    private Output output = new Output();

    @Valid
    @NotNull
    private Batch batch = new Batch();

    @Valid
    @NotNull
    private Datasource datasource = new Datasource();

    @NotEmpty
    private List<@Valid FieldDefinition> fields;

    public Output getOutput() {
        return output;
    }

    public void setOutput(Output output) {
        this.output = output;
    }

    public Batch getBatch() {
        return batch;
    }

    public void setBatch(Batch batch) {
        this.batch = batch;
    }

    public Datasource getDatasource() {
        return datasource;
    }

    public void setDatasource(Datasource datasource) {
        this.datasource = datasource;
    }

    public List<FieldDefinition> getFields() {
        return fields;
    }

    public void setFields(List<FieldDefinition> fields) {
        this.fields = fields;
    }

    public static class Output {

        @NotBlank
        private String directory;

        private String filePrefix = "HANDOFF_";

        private String fileSuffix = ".dat";

        private String encoding = "UTF-8";

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getFilePrefix() {
            return filePrefix;
        }

        public void setFilePrefix(String filePrefix) {
            this.filePrefix = filePrefix;
        }

        public String getFileSuffix() {
            return fileSuffix;
        }

        public void setFileSuffix(String fileSuffix) {
            this.fileSuffix = fileSuffix;
        }

        public String getEncoding() {
            return encoding;
        }

        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }
    }

    public static class Batch {

        @Positive
        private int chunkSize = 1000;

        @Positive
        private int pageSize = 1000;

        @Positive
        private int skipLimit = 10;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getSkipLimit() {
            return skipLimit;
        }

        public void setSkipLimit(int skipLimit) {
            this.skipLimit = skipLimit;
        }
    }

    public static class Datasource {

        @NotBlank
        private String selectClause;

        @NotBlank
        private String fromClause;

        private String whereClause;

        @NotBlank
        private String sortKey;

        public String getSelectClause() {
            return selectClause;
        }

        public void setSelectClause(String selectClause) {
            this.selectClause = selectClause;
        }

        public String getFromClause() {
            return fromClause;
        }

        public void setFromClause(String fromClause) {
            this.fromClause = fromClause;
        }

        public String getWhereClause() {
            return whereClause;
        }

        public void setWhereClause(String whereClause) {
            this.whereClause = whereClause;
        }

        public String getSortKey() {
            return sortKey;
        }

        public void setSortKey(String sortKey) {
            this.sortKey = sortKey;
        }
    }
}
