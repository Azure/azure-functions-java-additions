package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.sdktype.blob.BlobClientMetaData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlobClientMetaDataTest {

    @Test
    void testParseAndVerifyValid() {
        BlobClientMetaData meta = new BlobClientMetaData("com.azure.storage.blob.BlobClient", null);

        meta.setFieldValue("ContainerName", "mycontainer");
        meta.setFieldValue("BlobName", "myblob");
        meta.setFieldValue("Connection", "MY_ENV_VAR");

        // Should succeed
        meta.parseAndVerify();
        assertEquals("mycontainer", meta.getContainerName());
        assertEquals("myblob", meta.getBlobName());
        assertEquals("MY_ENV_VAR", meta.getConnectionEnvVar());
    }

    @Test
    void testParseAndVerifyMissingFields() {
        BlobClientMetaData meta = new BlobClientMetaData("com.azure.storage.blob.BlobClient", null);

        meta.setFieldValue("ContainerName", "mycontainer");
        meta.setFieldValue("BlobName", "myblob");
        // missing "Connection"

        assertThrows(IllegalArgumentException.class, meta::parseAndVerify);
    }
}
