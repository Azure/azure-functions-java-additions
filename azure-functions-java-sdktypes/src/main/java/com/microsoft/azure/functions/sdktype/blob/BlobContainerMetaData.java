package com.microsoft.azure.functions.sdktype.blob;

import java.lang.reflect.Parameter;
import java.util.*;

/**
 * MetaData for building a BlobContainerClient, storing fields such as containerName,
 * plus the environment variable name used for connection string or managed identity prefix.
 */
public class BlobContainerMetaData implements BlobMetaData {
    private final Map<String, Object> rawValues = new HashMap<>();

    private String containerName;
    private String connectionEnvVar;
    private final String fqcn;
    private final Parameter param;

    public BlobContainerMetaData(String fqcn, Parameter param) {
        this.fqcn = fqcn;
        this.param = param;
    }

    @Override
    public Set<String> getRequiredFields() {
        // Typically "ContainerName" and "Connection" for the environment variable prefix
        return new HashSet<>(Arrays.asList("ContainerName", "Connection"));
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
        // read fields from rawValues
        this.containerName = (String) rawValues.get("ContainerName");
        this.connectionEnvVar = (String) rawValues.get("Connection");

        if (containerName == null || containerName.isEmpty()) {
            throw new IllegalArgumentException("containerName is required for BlobContainerClient.");
        }
        if (connectionEnvVar == null || connectionEnvVar.isEmpty()) {
            throw new IllegalArgumentException("connectionEnvVar is required for BlobContainerClient.");
        }
    }

    @Override
    public Parameter getParam() {
        return param;
    }

    @Override
    public String getFqcn() {
        return fqcn;
    }

    // Typed getters for usage in hydrator/SDKType
    public String getContainerName() {
        return containerName;
    }

    public String getConnectionEnvVar() {
        return connectionEnvVar;
    }
}
