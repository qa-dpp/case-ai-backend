package com.fingertip.caseaibackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MindMap {
    private Root root;
    private String template;
    private String theme;
    private String version;
    private int base;

    @Data
    public static class Root {
        private NodeData data;
        private List<Node> children;
    }

    @Data
    public static class Node {
        private NodeData data;
        private List<Node> children;
    }

    @Data
    public static class NodeData {
        private String id;
        private long created;
        private String text;
    }
}
