package com.fingertip.caseaibackend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MarkdownNode {
    private int level;
    private String text;
    private List<MarkdownNode> children = new ArrayList<>();

    public MarkdownNode(int level, String text) {
        this.level = level;
        this.text = text;
    }
}
