package com.ss.doc.config;

import com.ss.doc.customizer.JavadocTagOperationCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.providers.JavadocProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Simple Secret OpenAPI 文档自动配置。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(prefix = "simple-secret.doc", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DocProperties.class)
public class SimpleSecretDocAutoConfiguration {

    /** 根据 starter 配置创建可被消费者覆盖的 OpenAPI 模型。 */
    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI simpleSecretOpenApi(DocProperties properties) {
        return DocOpenApiFactory.create(properties);
    }

    /** 创建可选的类 Javadoc 标签增强器。 */
    @Bean(name = "simpleSecretJavadocTagOperationCustomizer")
    @ConditionalOnMissingBean(name = "simpleSecretJavadocTagOperationCustomizer")
    @ConditionalOnProperty(
            prefix = "simple-secret.doc", name = "javadoc-tags-enabled", havingValue = "true")
    public JavadocTagOperationCustomizer simpleSecretJavadocTagOperationCustomizer(
            ObjectProvider<JavadocProvider> javadocProvider) {
        return new JavadocTagOperationCustomizer(javadocProvider.getIfAvailable());
    }
}
