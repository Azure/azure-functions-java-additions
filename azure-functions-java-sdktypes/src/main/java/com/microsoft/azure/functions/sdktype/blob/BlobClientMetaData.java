package com.microsoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;

import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Example metaData class for storing fields: containerName, blobName, etc.
 * We store them in a map or direct fields. We'll show a map approach.
 */
public class BlobClientMetaData implements SdkTypeMetaData {
    private final Map<String,Object> rawValues = new HashMap<>();

    // typed fields after parse
    private String containerName;
    private String blobName;
    private String connectionEnvVar;
    private String fqcn;
    private Parameter param;

    public BlobClientMetaData(String fqcn, Parameter param) {
        this.fqcn = fqcn;
        this.param = param;
    }

    @Override
    public Set<String> getRequiredFields() {
        return Set.of("ContainerName","BlobName","Connection");
    }

    @Override
    public Object getFieldValue(String key) {
        return rawValues.get(key);
    }

    @Override
    public void setFieldValue(String key, Object value) {
        rawValues.put(key, value);
    }

    @Override
    public void parseAndVerify() {
        // transform the raw values into typed fields
        this.containerName = (String) rawValues.get("ContainerName");
        this.blobName = (String) rawValues.get("BlobName");
        this.connectionEnvVar = (String) rawValues.get("Connection");

        // do checks
        if (containerName == null || containerName.isEmpty()) {
            throw new IllegalArgumentException("containerName is required");
        }
        if (blobName == null || blobName.isEmpty()) {
            throw new IllegalArgumentException("blobName is required");
        }
        if (connectionEnvVar == null || connectionEnvVar.isEmpty()) {
            throw new IllegalArgumentException("connectionEnvVar is required");
        }
    }

    @Override
    public Parameter getParam() {
        return param;
    }

    @Override
    public String getFqcn() { return fqcn; }

    public String getContainerName() { return containerName; }
    public String getBlobName() { return blobName; }
    public String getConnectionEnvVar() { return connectionEnvVar; }
}

