package com.pictures;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.Value;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Value
public class LookupMeta<T> {
    List<String> path;
    Function<JsonNode, T> getter;
    Function<Metadata, T> checker;
    BiConsumer<Metadata, T> setter;

}
