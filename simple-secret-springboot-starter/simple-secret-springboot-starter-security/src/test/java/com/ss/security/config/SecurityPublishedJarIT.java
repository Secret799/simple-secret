package com.ss.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证发布 Security jar 在无 WebMVC 和 Servlet 类时可由 Spring Boot 安全处理。 */
class SecurityPublishedJarIT {
    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    private static final String AUTO_CONFIGURATION =
            "com.ss.security.config.SimpleSecretSecurityAutoConfiguration";
    private static final String DISPATCHER_SERVLET =
            "org.springframework.web.servlet.DispatcherServlet";
    private static final String SERVLET = "jakarta.servlet.Servlet";

    @Test
    void shouldProcessPublishedImportWithoutWebClasses() throws Exception {
        Path buildDirectory = Path.of(property("simple-secret.security.published-build-directory"));
        String artifactId = property("simple-secret.security.published-artifact-id");
        String version = property("simple-secret.security.published-version");
        String finalName = property("simple-secret.security.published-final-name");
        Path publishedJar = buildDirectory.resolve(finalName + ".jar");

        assertThat(finalName).isEqualTo(artifactId + "-" + version);
        assertThat(Files.isRegularFile(publishedJar)).isTrue();

        try (URLClassLoader classLoader = new URLClassLoader(
                isolatedRuntimeUrls(publishedJar, buildDirectory), ClassLoader.getPlatformClassLoader())) {
            URL imports = classLoader.getResource(AUTO_CONFIGURATION_IMPORTS);
            assertThat(imports).isNotNull();
            assertThat(read(imports).lines().map(String::trim).filter(line -> !line.isEmpty()))
                    .containsExactly(AUTO_CONFIGURATION);
            assertThatNoException().isThrownBy(() -> runNonWebApplication(classLoader));
            assertThatThrownBy(() -> Class.forName(DISPATCHER_SERVLET, false, classLoader))
                    .isInstanceOf(ClassNotFoundException.class);
            assertThatThrownBy(() -> Class.forName(SERVLET, false, classLoader))
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    private static URL[] isolatedRuntimeUrls(Path publishedJar, Path buildDirectory) throws Exception {
        List<URL> urls = new ArrayList<>();
        urls.add(publishedJar.toUri().toURL());
        urls.add(buildDirectory.resolve("test-classes").toUri().toURL());
        for (String entry : System.getProperty("java.class.path").split(
                java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
            Path path = Path.of(entry).toAbsolutePath().normalize();
            if (path.startsWith(buildDirectory.toAbsolutePath().normalize())
                    || isWebServletOrTestOnlySaToken(path)) {
                continue;
            }
            urls.add(path.toUri().toURL());
        }
        return urls.toArray(URL[]::new);
    }

    private static boolean isWebServletOrTestOnlySaToken(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith("spring-webmvc-")
                || fileName.startsWith("spring-web-")
                || fileName.startsWith("jakarta.servlet-api-")
                || fileName.startsWith("tomcat-embed-")
                || fileName.startsWith("sa-token-spring-")
                || fileName.startsWith("sa-token-jakarta-servlet-")
                || fileName.startsWith("sa-token-jackson-");
    }

    private static void runNonWebApplication(ClassLoader classLoader) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        Object context = null;
        try {
            thread.setContextClassLoader(classLoader);
            Class<?> applicationClass = Class.forName(
                    SecurityPublishedNonWebApplication.class.getName(), true, classLoader);
            Class<?> springApplicationClass = Class.forName(
                    "org.springframework.boot.SpringApplication", true, classLoader);
            Object application = springApplicationClass.getConstructor(Class[].class)
                    .newInstance((Object) new Class<?>[]{applicationClass});
            Class<?> webApplicationType = Class.forName(
                    "org.springframework.boot.WebApplicationType", true, classLoader);
            springApplicationClass.getMethod("setWebApplicationType", webApplicationType)
                    .invoke(application, webApplicationType.getField("NONE").get(null));
            springApplicationClass.getMethod("setDefaultProperties", Map.class)
                    .invoke(application, Map.of(
                            "simple-secret.security.enabled", "true",
                            "spring.main.banner-mode", "off"));
            context = springApplicationClass.getMethod("run", String[].class)
                    .invoke(application, (Object) new String[0]);

            Class<?> propertiesClass = Class.forName(
                    "com.ss.security.config.SecurityProperties", true, classLoader);
            Method getBeanNamesForType = context.getClass()
                    .getMethod("getBeanNamesForType", Class.class);
            assertThat((String[]) getBeanNamesForType.invoke(context, propertiesClass)).isEmpty();
        } finally {
            if (context != null) {
                context.getClass().getMethod("close").invoke(context);
            }
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    private static String read(URL resource) throws Exception {
        try (var input = resource.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        assertThat(value).as(name).isNotBlank();
        return value;
    }
}

@SpringBootConfiguration
@EnableAutoConfiguration
class SecurityPublishedNonWebApplication {
}
