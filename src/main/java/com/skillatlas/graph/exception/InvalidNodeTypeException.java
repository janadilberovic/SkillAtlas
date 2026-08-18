package com.skillatlas.graph.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.skillatlas.graph.enums.GraphNodeKind;

public class InvalidNodeTypeException extends RuntimeException {

    public InvalidNodeTypeException(String value) {
        super("Unknown node type '" + value + "'. Expected one of: " + Arrays.stream(GraphNodeKind.values())
                .map(kind -> kind.name().toLowerCase())
                .collect(Collectors.joining(", ")));
    }
}
