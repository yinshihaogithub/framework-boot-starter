package com.framework.starter.dependency;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkStarterDependencyContractTest {

    private static final List<String> DEFAULT_FRAMEWORK_MODULES = List.of(
            "framework-core",
            "framework-web",
            "framework-auth",
            "framework-security",
            "framework-cache",
            "framework-lock",
            "framework-idempotent",
            "framework-crypto",
            "framework-log",
            "framework-rate-limiter",
            "framework-mq",
            "framework-retry",
            "framework-tools",
            "framework-notify",
            "framework-local-message",
            "framework-excel",
            "framework-datasource",
            "framework-redis",
            "framework-feign",
            "framework-monitor",
            "framework-file"
    );

    @Test
    void defaultStarterAggregatesCommonModulesButKeepsXxlJobOptIn() throws Exception {
        Path root = projectRoot();

        assertThat(moduleNames(root.resolve("pom.xml"))).contains("framework-job");

        List<String> starterModules = dependencies(root.resolve("framework-starter/pom.xml")).stream()
                .filter(dependency -> "com.framework".equals(dependency.groupId()))
                .map(Dependency::artifactId)
                .toList();

        assertThat(starterModules)
                .containsExactlyElementsOf(DEFAULT_FRAMEWORK_MODULES)
                .doesNotContain("framework-job");
    }

    private static List<Dependency> dependencies(Path pom) throws Exception {
        Element dependencies = directChild(parse(pom).getDocumentElement(), "dependencies");
        if (dependencies == null) {
            return List.of();
        }
        List<Dependency> result = new ArrayList<>();
        for (Element dependency : directChildren(dependencies, "dependency")) {
            result.add(new Dependency(
                    textOf(dependency, "groupId"),
                    textOf(dependency, "artifactId")
            ));
        }
        return result;
    }

    private static List<String> moduleNames(Path pom) throws Exception {
        Element modules = directChild(parse(pom).getDocumentElement(), "modules");
        if (modules == null) {
            return List.of();
        }
        return directChildren(modules, "module").stream()
                .map(Element::getTextContent)
                .map(String::trim)
                .toList();
    }

    private static Document parse(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(pom.toFile());
        document.getDocumentElement().normalize();
        return document;
    }

    private static String textOf(Element element, String childName) {
        Element child = directChild(element, childName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element directChild(Element element, String childName) {
        for (Element child : directChildren(element, childName)) {
            return child;
        }
        return null;
    }

    private static List<Element> directChildren(Element element, String childName) {
        NodeList nodes = element.getChildNodes();
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element child && childName.equals(child.getTagName())) {
                result.add(child);
            }
        }
        return result;
    }

    private static Path projectRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        if (Files.exists(cwd.resolve("framework-starter/pom.xml"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve("framework-starter/pom.xml"))) {
            return parent;
        }
        throw new IllegalStateException("Cannot locate framework project root from " + cwd);
    }

    private record Dependency(String groupId, String artifactId) {
    }
}
