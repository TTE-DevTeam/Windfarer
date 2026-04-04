package net.countercraft.movecraft.util;

import net.countercraft.movecraft.Movecraft;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public class TypeDependency {

    static class DependencyNode {
        final File file;
        final @Nullable String parentName;
        @Nullable DependencyNode parentNode;
        final List<DependencyNode> children = new ArrayList<>();

        public DependencyNode(File file, String parentName) {
            this.file = file;
            this.parentName = parentName;
        }

        public void print(Logger logger) {
            print(logger, "", true);
        }
        private void print(Logger logger, String prefix, boolean isEndNode) {
            logger.info(prefix + (isEndNode ? "└── " : "├── ") + this.file.getName());

            for (int i = 0; i < children.size(); i++) {
                boolean last = i == (children.size() - 1);
                children.get(i).print(logger, prefix + (last ? "    " : "│   "), last);
            }
        }
    }

    public static Queue<File> buildLoadingQueue(Set<Path> files) {
        List<DependencyNode> rootNodes = findRootNodes(files);
        printDependencyTree(rootNodes);

        Queue<File> queue = new LinkedList<>();
        Set<DependencyNode> visited = new HashSet<>();

        for (DependencyNode node : rootNodes) {
            processElementForQueue(node, queue, visited);
        }

        return queue;
    }

    private static void processElementForQueue(DependencyNode node, Queue<File> queue, Set<DependencyNode> visited) {
        if (visited.contains(node))
            return;

        visited.add(node);
        queue.add(node.file);

        for (DependencyNode child : node.children) {
            processElementForQueue(child, queue, visited);
        }
    }

    private static void printDependencyTree(List<DependencyNode> rootNodes) {
        final Logger logger = Movecraft.getInstance().getLogger();
        logger.info("Type hierarchy tree:");
        for (DependencyNode node : rootNodes) {
            node.print(logger);
        }
    }

    private static List<DependencyNode> findRootNodes(Set<Path> files) {
        List<DependencyNode> result = new ArrayList<>();
        Map<String, DependencyNode> nodes = new HashMap<>();

        // Generate nodes first
        // region generate nodes
        for (Path path : files) {
            final File file = path.toFile();
            if (!file.exists())
                continue;
            final InputStream input;
            try {
                input = new FileInputStream(file);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
                continue;
            }
            try(input) {
                FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                final String name = file.getName().substring(0, file.getName().lastIndexOf('.')).toUpperCase();
                nodes.put(name, new DependencyNode(file, yaml.getString("parent", "").toUpperCase()));
            }
            catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }
        // endregion generate nodes

        // region calculate tree
        for (DependencyNode node : nodes.values()) {
            if (node.parentName != null && !node.parentName.trim().isEmpty()) {
                DependencyNode parent = nodes.get(node.parentName);
                if (parent == null) {
                    Movecraft.getInstance().getLogger().warning(String.format("Unable to find parent <%s> for file <%s>! File will probably not work properly!", node.parentName, node.file.getName()));
                    result.add(node);
                } else {
                    node.parentNode = parent;
                    parent.children.add(node);
                }
            } else {
                result.add(node);
            }
        }
        // endregion calculate tree

        // region scan for cycles
        Set<DependencyNode> toRemove = new HashSet<>();
        findCyclicReferences(result, toRemove);
        result.removeAll(toRemove);
        // endregion scan for cycles

        return result;
    }

    private static void findCyclicReferences(List<DependencyNode> result, Set<DependencyNode> toRemove) {
        Set<DependencyNode> currentlyAnalyzing = new HashSet<>();
        Set<DependencyNode> visited = new HashSet<>();

        for (DependencyNode node : result) {
            if (!runDFS(node, currentlyAnalyzing, visited)) {
                toRemove.add(node);
            }
        }
    }

    /*
     * Check all parents recursively of the node, if the parent is within the currentlyAnalyzing set, it is a cyclic reference
     */
    private static boolean runDFS(DependencyNode node, Set<DependencyNode> currentlyAnalyzing, Set<DependencyNode> visited) {
        // we already know this file, all good
        if (visited.contains(node)) {
            return true;
        }
        if (currentlyAnalyzing.contains(node)) {
            Movecraft.getInstance().getLogger().warning(String.format("Loading cycle detected! File: <%s>", node.file.getName()));
            return false;
        }

        currentlyAnalyzing.add(node);
        boolean result = true;
        for (DependencyNode child : node.children) {
            result &= runDFS(child, currentlyAnalyzing, visited);
            if (!result) {
                break;
            }
        }

        currentlyAnalyzing.remove(node);
        visited.add(node);

        return result;
    }

}
