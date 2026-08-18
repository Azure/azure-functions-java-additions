package com.microsoft.azure.functions.spi.inject;

import com.google.gson.Gson;

public interface GsonInstanceInjector {
    Gson getGsonInstance() throws Exception;
}
