package com.framework.tools.tree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TreeUtilsTest {

    @Test
    void buildsTreeWhenRootParentIdIsNull() {
        Node root = new Node(1L, null);
        Node child = new Node(2L, 1L);
        Node grandChild = new Node(3L, 2L);

        List<Node> roots = TreeUtils.buildTree(
                List.of(root, child, grandChild),
                node -> node.id,
                node -> node.parentId,
                (node, children) -> node.children = children,
                node -> node.parentId == null);

        assertThat(roots).containsExactly(root);
        assertThat(root.children).containsExactly(child);
        assertThat(child.children).containsExactly(grandChild);
    }

    @Test
    void flattensTreeInPreOrder() {
        Node root = new Node(1L, null);
        Node child = new Node(2L, 1L);
        root.children = new ArrayList<>(List.of(child));

        assertThat(TreeUtils.flatten(List.of(root), node -> node.children))
                .containsExactly(root, child);
        assertThat(TreeUtils.<Node>flatten(null, node -> node.children)).isEmpty();
    }

    @Test
    void buildTreeReturnsEmptyForNullInput() {
        assertThat(TreeUtils.<Node, Long>buildTree(
                null,
                node -> node.id,
                node -> node.parentId,
                (node, children) -> node.children = children,
                node -> node.parentId == null
        )).isEmpty();
    }

    @Test
    void buildTreeRejectsDuplicateIdsAndCycles() {
        assertThatThrownBy(() -> TreeUtils.buildTree(
                List.of(new Node(1L, null), new Node(1L, null)),
                node -> node.id,
                node -> node.parentId,
                (node, children) -> node.children = children,
                node -> node.parentId == null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate tree node id");

        assertThatThrownBy(() -> TreeUtils.buildTree(
                List.of(new Node(1L, 1L)),
                node -> node.id,
                node -> node.parentId,
                (node, children) -> node.children = children,
                node -> true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tree cycle detected");
    }

    private static final class Node {
        private final Long id;
        private final Long parentId;
        private List<Node> children = new ArrayList<>();

        private Node(Long id, Long parentId) {
            this.id = id;
            this.parentId = parentId;
        }
    }
}
