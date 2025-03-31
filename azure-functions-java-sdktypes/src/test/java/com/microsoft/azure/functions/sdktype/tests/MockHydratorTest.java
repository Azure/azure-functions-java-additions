package com.microsoft.azure.functions.sdktype.tests;

import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MockHydratorTest {

    static class FakeMetaData implements SdkTypeMetaData {
        @Override public Object getFieldValue(String key) { return null; }
        @Override public void setFieldValue(String key, Object value) {}
        @Override public void parseAndVerify() {}
        @Override public java.lang.reflect.Parameter getParam() { return null; }
        @Override public String getFqcn() { return "FakeFqcn"; }
    }

    @Test
    void testBuildInstanceCallsHydrator() throws Exception {
        final SdkTypeHydrator<SdkTypeMetaData> hydrator = new SdkTypeHydrator<SdkTypeMetaData>() {
            @Override
            public Object createInstance(SdkTypeMetaData metaData) {
                return "ConstructedObject";
            }
        };

        final SdkTypeMetaData meta = new FakeMetaData();

        SdkType<SdkTypeMetaData> sdkType = new SdkType<SdkTypeMetaData>() {
            @Override
            public SdkTypeMetaData getMetaData() {
                return meta;
            }

            @Override
            public SdkTypeHydrator<SdkTypeMetaData> getHydrator() {
                return hydrator;
            }
        };

        Object result = sdkType.buildInstance();
        assertEquals("ConstructedObject", result);
    }
}
