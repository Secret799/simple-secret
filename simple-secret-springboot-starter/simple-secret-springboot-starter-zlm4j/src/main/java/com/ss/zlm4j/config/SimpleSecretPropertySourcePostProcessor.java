package com.ss.zlm4j.config;

import com.ss.zlm4j.config.annotation.SimpleSecretPropertySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * {@code @SimpleSecretPropertySource} 配置读取后处理器。
 *
 * <p>扫描标注了 {@link SimpleSecretPropertySource} 的 Bean，将其声明的资源
 * （yml/ini 等）加载进 Spring Environment。迁移自 honeybee 的
 * {@code HoneybeePropertySourcePostProcessor}，去掉 hutool 与 lombok 依赖。</p>
 */
public class SimpleSecretPropertySourcePostProcessor
        implements BeanFactoryPostProcessor, InitializingBean, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleSecretPropertySourcePostProcessor.class);

    private final ResourceLoader resourceLoader;
    private final List<PropertySourceLoader> propertySourceLoaders;

    public SimpleSecretPropertySourcePostProcessor() {
        this.resourceLoader = new DefaultResourceLoader();
        this.propertySourceLoaders = SpringFactoriesLoader.loadFactories(
                PropertySourceLoader.class, getClass().getClassLoader());
    }

    private static void loadPropertySource(String location, Resource resource,
                                           PropertySourceLoader loader,
                                           List<PropertySource<?>> sourceList) {
        if (resource.exists()) {
            String name = "simpleSecretPropertySource: [" + location + "]";
            try {
                sourceList.addAll(loader.load(name, resource));
            } catch (Exception e) {
                throw new IllegalStateException("Unable to load property source: " + location, e);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        LOG.info("SimpleSecretPropertySourcePostProcessor process @SimpleSecretPropertySource bean.");
        String[] beanNamesForAnnotation = beanFactory.getBeanNamesForAnnotation(SimpleSecretPropertySource.class);
        // 没有 @SimpleSecretPropertySource 注解，跳出
        if (beanNamesForAnnotation.length == 0) {
            LOG.warn("Not found @SimpleSecretPropertySource on spring bean class.");
            return;
        }
        // 组装资源
        List<PropertyFile> propertyFileList = new ArrayList<>();
        for (String beanName : beanNamesForAnnotation) {
            Class<?> beanClass = beanFactory.getType(beanName);
            SimpleSecretPropertySource propertySource = AnnotationUtils.getAnnotation(
                    Objects.requireNonNull(beanClass), SimpleSecretPropertySource.class);
            if (propertySource == null) {
                continue;
            }
            int order = propertySource.order();
            boolean loadActiveProfile = propertySource.loadActiveProfile();
            String[] locations = propertySource.value();
            Stream.of(locations).forEach(location ->
                    propertyFileList.add(new PropertyFile(order, location, loadActiveProfile)));
        }

        // 装载 PropertySourceLoader
        Map<String, PropertySourceLoader> loaderMap = new HashMap<>(16);
        for (PropertySourceLoader loader : propertySourceLoaders) {
            String[] loaderExtensions = loader.getFileExtensions();
            for (String extension : loaderExtensions) {
                loaderMap.put(extension, loader);
            }
        }
        // 去重，排序
        List<PropertyFile> sortedPropertyList = propertyFileList.stream()
                .distinct()
                .sorted()
                .toList();
        ConfigurableEnvironment environment = beanFactory.getBean(ConfigurableEnvironment.class);
        MutablePropertySources propertySources = environment.getPropertySources();
        // 只支持 activeProfiles，没有必要支持 spring.profiles.include。
        String[] activeProfiles = environment.getActiveProfiles();
        ArrayList<PropertySource<?>> propertySourceList = new ArrayList<>();
        for (String profile : activeProfiles) {
            for (PropertyFile propertyFile : sortedPropertyList) {
                // 不加载 ActiveProfile 的配置文件
                if (!propertyFile.loadActiveProfile) {
                    continue;
                }
                String extension = propertyFile.getExtension();
                PropertySourceLoader loader = loaderMap.get(extension);
                if (loader == null) {
                    throw new IllegalArgumentException(
                            "Can't find PropertySourceLoader for PropertySource extension:" + extension);
                }
                String location = propertyFile.getLocation();
                String filePath = StringUtils.stripFilenameExtension(location);
                String profiledLocation = filePath + "-" + profile + "." + extension;
                Resource resource = resourceLoader.getResource(profiledLocation);
                loadPropertySource(profiledLocation, resource, loader, propertySourceList);
            }
        }
        // 本身的 Resource
        for (PropertyFile propertyFile : sortedPropertyList) {
            String extension = propertyFile.getExtension();
            PropertySourceLoader loader = loaderMap.get(extension);
            String location = propertyFile.getLocation();
            Resource resource = resourceLoader.getResource(location);
            loadPropertySource(location, resource, loader, propertySourceList);
        }
        // 转存
        for (PropertySource<?> propertySource : propertySourceList) {
            propertySources.addLast(propertySource);
        }
    }

    @Override
    public void afterPropertiesSet() {
        LOG.info("SimpleSecretPropertySourcePostProcessor init.");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static class PropertyFile implements Comparable<PropertyFile> {
        private final int order;
        private final String location;
        private final String extension;
        private final boolean loadActiveProfile;

        PropertyFile(int order, String location, boolean loadActiveProfile) {
            this.order = order;
            this.location = location;
            this.loadActiveProfile = loadActiveProfile;
            this.extension = Objects.requireNonNull(StringUtils.getFilenameExtension(location));
        }

        int getOrder() {
            return order;
        }

        String getLocation() {
            return location;
        }

        String getExtension() {
            return extension;
        }

        boolean isLoadActiveProfile() {
            return loadActiveProfile;
        }

        @Override
        public int compareTo(PropertyFile other) {
            return Integer.compare(this.order, other.order);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PropertyFile that)) {
                return false;
            }
            return order == that.order && loadActiveProfile == that.loadActiveProfile
                    && location.equals(that.location) && extension.equals(that.extension);
        }

        @Override
        public int hashCode() {
            return Objects.hash(order, location, extension, loadActiveProfile);
        }
    }
}
