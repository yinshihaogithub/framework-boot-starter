package com.framework.tools.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 树形结构工具
 */
public class TreeUtils {

    /**
     * 列表转树
     *
     * @param list    平铺列表
     * @param idGetter   ID提取器
     * @param parentGetter 父ID提取器
     * @param childrenSetter 子节点设置器
     * @param rootPredicate 根节点判断（parentId 为 null 或 0）
     */
    public static <T, ID> List<T> buildTree(
            List<T> list,
            java.util.function.Function<T, ID> idGetter,
            java.util.function.Function<T, ID> parentGetter,
            java.util.function.BiConsumer<T, List<T>> childrenSetter,
            java.util.function.Predicate<T> rootPredicate) {

        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Objects.requireNonNull(idGetter, "idGetter must not be null");
        Objects.requireNonNull(parentGetter, "parentGetter must not be null");
        Objects.requireNonNull(childrenSetter, "childrenSetter must not be null");
        Objects.requireNonNull(rootPredicate, "rootPredicate must not be null");

        Map<ID, T> idMap = new HashMap<>();
        Map<ID, List<T>> parentMap = new HashMap<>();
        for (T node : list) {
            if (node == null) {
                throw new IllegalArgumentException("Tree node must not be null");
            }
            ID id = idGetter.apply(node);
            if (id == null) {
                throw new IllegalArgumentException("Tree node id must not be null");
            }
            if (idMap.putIfAbsent(id, node) != null) {
                throw new IllegalArgumentException("Duplicate tree node id: " + id);
            }
            parentMap.computeIfAbsent(parentGetter.apply(node), ignored -> new ArrayList<>()).add(node);
        }

        List<T> roots = list.stream()
                .filter(rootPredicate)
                .collect(Collectors.toList());

        roots.forEach(node -> setChildren(node, parentMap, idGetter, childrenSetter, new HashSet<>()));
        return roots;
    }

    private static <T, ID> void setChildren(
            T node,
            Map<ID, List<T>> parentMap,
            java.util.function.Function<T, ID> idGetter,
            java.util.function.BiConsumer<T, List<T>> childrenSetter,
            Set<ID> path) {

        ID id = idGetter.apply(node);
        if (!path.add(id)) {
            throw new IllegalArgumentException("Tree cycle detected at id: " + id);
        }
        try {
            List<T> children = parentMap.get(id);
            if (children != null && !children.isEmpty()) {
                childrenSetter.accept(node, children);
                for (T child : children) {
                    setChildren(child, parentMap, idGetter, childrenSetter, path);
                }
            }
        } finally {
            path.remove(id);
        }
    }

    /**
     * 树转列表（扁平化）
     */
    public static <T> List<T> flatten(
            List<T> tree,
            java.util.function.Function<T, List<T>> childrenGetter) {

        List<T> result = new ArrayList<>();
        flattenRecursive(tree, childrenGetter, result);
        return result;
    }

    private static <T> void flattenRecursive(
            List<T> nodes,
            java.util.function.Function<T, List<T>> childrenGetter,
            List<T> result) {

        if (nodes == null) return;
        for (T node : nodes) {
            result.add(node);
            flattenRecursive(childrenGetter.apply(node), childrenGetter, result);
        }
    }
}
