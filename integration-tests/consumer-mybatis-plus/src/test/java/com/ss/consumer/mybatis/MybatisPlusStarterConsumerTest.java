package com.ss.consumer.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.ss.mybatis.audit.AuditContext;
import com.ss.mybatis.audit.AuditContextProvider;
import com.ss.mybatis.audit.SimpleSecretMetaObjectHandler;
import com.ss.mybatis.cache.MybatisPlusStatusFieldCacheManager;
import com.ss.mybatis.config.MybatisStarterProperties;
import com.ss.mybatis.page.PageQuery;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证仓库外第三方应用只依赖发布 starter 即可使用 MyBatis-Plus 增强。 */
class MybatisPlusStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void shouldUseBomManagedStarterWithoutExplicitVersion() throws Exception {
        Element project = parsePom(Path.of("pom.xml"));
        Element dependency = dependency(project, "com.ss",
                "simple-secret-springboot-starter-mybatis-plus");

        assertThat(text(dependency, "version")).isNull();
    }

    @Test
    void shouldDiscoverPublishedAutoConfiguration() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(MybatisStarterProperties.class)
                .hasSingleBean(AuditContextProvider.class)
                .hasSingleBean(SimpleSecretMetaObjectHandler.class)
                .hasSingleBean(MybatisPlusInterceptor.class));
    }

    @Test
    void shouldExposeSafePaginationApi() {
        PageQuery query = new PageQuery();
        query.setPageNum(2L);
        query.setPageSize(25L);
        query.setOrderByColumn("createTime");
        query.setDirection("desc");

        assertThat(query.build(100L).getCurrent()).isEqualTo(2L);
        assertThat(query.build(100L).orders().get(0).getColumn())
                .isEqualTo("create_time");
    }

    @Test
    void shouldBackOffForConsumerAuditBeans() {
        runner.withUserConfiguration(ConsumerAuditConfiguration.class)
                .run(context -> {
                    AuditContextProvider provider = context.getBean(AuditContextProvider.class);
                    assertThat(provider.current()).isEqualTo(new AuditContext(9L, 3L));
                    assertThat(context.getBean(MetaObjectHandler.class))
                            .isSameAs(ConsumerAuditConfiguration.HANDLER);
                });
    }

    @Test
    void shouldAllowConsumerDefinedStatusCacheWithoutDatabaseAccess() {
        DeviceStatusCache cache = new DeviceStatusCache();

        cache.record("device-1", false);
        assertThat(cache.get("device-1")).isEqualTo("1");
        cache.cancel("device-1", false);
        assertThat(cache.get("device-1")).isEqualTo("0");
        cache.close();
    }

    private static Element parsePom(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private static Element dependency(Element project, String groupId, String artifactId) {
        NodeList nodes = project.getElementsByTagName("dependency");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element dependency = (Element) nodes.item(index);
            if (groupId.equals(text(dependency, "groupId"))
                    && artifactId.equals(text(dependency, "artifactId"))) {
                return dependency;
            }
        }
        throw new AssertionError("Missing dependency: " + groupId + ":" + artifactId);
    }

    private static String text(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    static class ConsumerApplication {

        @Bean
        SqlSessionFactory sqlSessionFactory() {
            return (SqlSessionFactory) Proxy.newProxyInstance(
                    SqlSessionFactory.class.getClassLoader(),
                    new Class<?>[]{SqlSessionFactory.class},
                    (proxy, method, args) -> null);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerAuditConfiguration {
        private static final AuditContextProvider PROVIDER =
                () -> new AuditContext(9L, 3L);
        private static final MetaObjectHandler HANDLER =
                new SimpleSecretMetaObjectHandler(PROVIDER);

        @Bean
        AuditContextProvider consumerAuditContextProvider() {
            return PROVIDER;
        }

        @Bean
        MetaObjectHandler consumerMetaObjectHandler() {
            return HANDLER;
        }
    }

    private static final class DeviceStatusCache extends
            MybatisPlusStatusFieldCacheManager<DeviceEntity, DeviceService> {

        private DeviceStatusCache() {
            super(DeviceEntity.class, Duration.ofSeconds(30));
        }

        @Override
        protected DeviceService service() {
            return null;
        }

        @Override
        protected SFunction<DeviceEntity, ?> keyField() {
            return DeviceEntity::getId;
        }

        @Override
        protected SFunction<DeviceEntity, ?> valueField() {
            return DeviceEntity::getStatus;
        }
    }

    private interface DeviceService extends IService<DeviceEntity> {
    }

    private static final class DeviceEntity {
        private String id;
        private String status;

        public String getId() {
            return id;
        }

        public String getStatus() {
            return status;
        }
    }
}
