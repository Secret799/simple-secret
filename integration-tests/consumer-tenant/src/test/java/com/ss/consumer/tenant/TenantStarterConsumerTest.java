package com.ss.consumer.tenant;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ss.tenant.config.TenantProperties;
import com.ss.tenant.context.TenantContext;
import com.ss.tenant.context.TenantContextProvider;
import com.ss.tenant.exception.TenantException;
import com.ss.tenant.interceptor.SimpleSecretTenantLineInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证仓库外应用只依赖发布 starter 即可获得真实 SQL 租户隔离。 */
class TenantStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:tenant_consumer;DB_CLOSE_DELAY=-1",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=");

    @Test
    void shouldUseBomManagedStarterWithoutExplicitVersion() throws Exception {
        Element project = parsePom(Path.of("pom.xml"));
        Element dependency = dependency(project, "com.ss",
                "simple-secret-springboot-starter-tenant");

        assertThat(text(dependency, "version")).isNull();
    }

    @Test
    void shouldDiscoverPublishedAutoConfigurationInCorrectOrder() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(TenantProperties.class);
            assertThat(context).hasSingleBean(TenantContext.class);
            assertThat(context).hasSingleBean(TenantLineInnerInterceptor.class);
            assertThat(context.getBean(MybatisPlusInterceptor.class).getInterceptors())
                    .hasExactlyElementsOfTypes(
                            SimpleSecretTenantLineInnerInterceptor.class,
                            PaginationInnerInterceptor.class,
                            OptimisticLockerInnerInterceptor.class);
        });
    }

    @Test
    void shouldFilterRealQueriesAndFailClosedWithoutTenant() {
        runner.run(context -> {
            prepareTable(context.getBean(JdbcTemplate.class));
            TenantContext tenantContext = context.getBean(TenantContext.class);
            TenantOrderMapper mapper = context.getBean(TenantOrderMapper.class);

            assertThat(tenantContext.callWithTenant("tenant-a", mapper::findNames))
                    .containsExactly("alpha", "beta");
            assertThat(tenantContext.callWithTenant("tenant-b", mapper::findNames))
                    .containsExactly("gamma");
            assertThatThrownBy(mapper::findNames)
                    .hasRootCauseInstanceOf(TenantException.class);
            assertThat(tenantContext.callWithoutTenant(mapper::findNames))
                    .containsExactly("alpha", "beta", "gamma");
        });
    }

    @Test
    void shouldUseConsumerTenantProvider() {
        runner.withUserConfiguration(ConsumerTenantProviderConfiguration.class)
                .run(context -> {
                    prepareTable(context.getBean(JdbcTemplate.class));
                    assertThat(context.getBean(TenantOrderMapper.class).findNames())
                            .containsExactly("gamma");
                });
    }

    @Test
    void shouldProtectRealInsertAndUpdateStatements() {
        runner.run(context -> {
            prepareTable(context.getBean(JdbcTemplate.class));
            TenantContext tenantContext = context.getBean(TenantContext.class);
            TenantOrderMapper mapper = context.getBean(TenantOrderMapper.class);

            assertThat(tenantContext.callWithTenant(
                    "tenant-a", () -> mapper.insertOrder(4L, "delta"))).isEqualTo(1);
            assertThat(tenantContext.callWithTenant("tenant-a", mapper::findNames))
                    .containsExactly("alpha", "beta", "delta");

            assertThatThrownBy(() -> tenantContext.callWithTenant(
                    "tenant-a", () -> mapper.insertForTenant(5L, "tenant-b", "forged")))
                    .hasRootCauseInstanceOf(TenantException.class);
            assertThatThrownBy(() -> tenantContext.callWithTenant(
                    "tenant-a", () -> mapper.moveOrder(1L, "tenant-b")))
                    .hasRootCauseInstanceOf(TenantException.class);

            assertThat(tenantContext.callWithTenant(
                    "tenant-a", () -> mapper.renameOrder(3L, "hidden"))).isZero();
            assertThat(tenantContext.callWithTenant(
                    "tenant-a", () -> mapper.renameOrder(1L, "renamed"))).isEqualTo(1);
            assertThat(tenantContext.callWithTenant("tenant-b", mapper::findNames))
                    .containsExactly("gamma");
        });
    }

    private static void prepareTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("drop table if exists tenant_orders");
        jdbcTemplate.execute("create table tenant_orders ("
                + "id bigint primary key, tenant_id varchar(64) not null, name varchar(64))");
        jdbcTemplate.update(
                "insert into tenant_orders (id, tenant_id, name) values (?, ?, ?)",
                1L, "tenant-a", "alpha");
        jdbcTemplate.update(
                "insert into tenant_orders (id, tenant_id, name) values (?, ?, ?)",
                2L, "tenant-a", "beta");
        jdbcTemplate.update(
                "insert into tenant_orders (id, tenant_id, name) values (?, ?, ?)",
                3L, "tenant-b", "gamma");
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
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = TenantOrderMapper.class)
    static class ConsumerApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerTenantProviderConfiguration {

        @Bean
        TenantContextProvider tenantContextProvider() {
            return () -> "tenant-b";
        }
    }
}

@Mapper
interface TenantOrderMapper {

    @Select("select name from tenant_orders order by id")
    List<String> findNames();

    @Insert("insert into tenant_orders (id, name) values (#{id}, #{name})")
    int insertOrder(@Param("id") long id, @Param("name") String name);

    @Insert("insert into tenant_orders (id, tenant_id, name) "
            + "values (#{id}, #{tenantId}, #{name})")
    int insertForTenant(
            @Param("id") long id,
            @Param("tenantId") String tenantId,
            @Param("name") String name);

    @Update("update tenant_orders set tenant_id = #{tenantId} where id = #{id}")
    int moveOrder(@Param("id") long id, @Param("tenantId") String tenantId);

    @Update("update tenant_orders set name = #{name} where id = #{id}")
    int renameOrder(@Param("id") long id, @Param("name") String name);
}
