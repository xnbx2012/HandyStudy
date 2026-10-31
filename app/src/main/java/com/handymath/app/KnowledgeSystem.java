package com.handymath.app;

import org.json.*;

import java.util.*;

/** Bank-owned ontology: stable node IDs, validated parent links, no fabricated mastery data. */
final class KnowledgeSystem {
    static final class Node {
        final String id, parent, name;

        Node(String id, String parent, String name) {
            this.id = id;
            this.parent = parent;
            this.name = name;
        }
    }

    final Map<String, Node> nodes = new LinkedHashMap<>();

    KnowledgeSystem(JSONObject system) throws Exception {
        if (system == null) return;
        JSONArray list = system.optJSONArray("nodes");
        if (list == null) throw new java.io.IOException("knowledgeSystem 缺少 nodes");
        if (list.length() > 3000) throw new java.io.IOException("知识节点过多");
        for (int i = 0; i < list.length(); i++) {
            JSONObject n = list.getJSONObject(i);
            String id = n.getString("id"),
                    parent = n.optString("parentId"),
                    name = n.getString("name");
            if (id.isBlank() || name.isBlank() || nodes.containsKey(id))
                throw new java.io.IOException("知识节点 ID / 名称无效或重复");
            nodes.put(id, new Node(id, parent, name));
        }
        for (Node n : nodes.values()) {
            Set<String> visited = new HashSet<>();
            Node cursor = n;
            while (cursor != null) {
                if (!visited.add(cursor.id)) throw new java.io.IOException("知识体系存在循环");
                if (!cursor.parent.isEmpty() && !nodes.containsKey(cursor.parent))
                    throw new java.io.IOException("知识体系缺少父节点");
                cursor = nodes.get(cursor.parent);
            }
        }
    }

    List<Node> children(String parent) {
        List<Node> out = new ArrayList<>();
        for (Node n : nodes.values()) if (n.parent.equals(parent)) out.add(n);
        return out;
    }

    boolean under(String id, String ancestor) {
        if (ancestor.isEmpty()) return true;
        Node n = nodes.get(id);
        while (n != null) {
            if (n.id.equals(ancestor)) return true;
            n = nodes.get(n.parent);
        }
        return false;
    }

    String path(String id) {
        List<String> parts = new ArrayList<>();
        Node n = nodes.get(id);
        while (n != null) {
            parts.add(n.name);
            n = nodes.get(n.parent);
        }
        Collections.reverse(parts);
        return String.join(" > ", parts);
    }
}
