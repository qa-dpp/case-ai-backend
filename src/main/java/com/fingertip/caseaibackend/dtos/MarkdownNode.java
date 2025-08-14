package com.fingertip.caseaibackend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MarkdownNode {
    int depth;       // 节点深度（层级）
    String text;     // 节点文本
    boolean isList;  // 是否为列表项
    List<MarkdownNode> children = new ArrayList<>(); // 子节点

    public MarkdownNode(int depth, String text, boolean isList) {
        this.depth = depth;
        this.text = text;
        this.isList = isList;
    }
}
