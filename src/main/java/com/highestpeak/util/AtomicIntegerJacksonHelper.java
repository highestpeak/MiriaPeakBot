package com.highestpeak.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIntegerJacksonHelper {
    public static class AtomicIntegerSerializer extends JsonSerializer<AtomicInteger> {

        @Override
        public void serialize(AtomicInteger tmpInt,
                              JsonGenerator jsonGenerator,
                              SerializerProvider serializerProvider)
                throws IOException, JsonProcessingException {
            jsonGenerator.writeObject(tmpInt.toString());
        }
    }

    public static class AtomicIntegerDeserializer extends JsonDeserializer<AtomicInteger> {


        @Override
        public AtomicInteger deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException,
                JsonProcessingException {
            return jp.getValueAsInt() <= 0 ? new AtomicInteger(1) : new AtomicInteger(jp.getValueAsInt());
        }
    }
}
