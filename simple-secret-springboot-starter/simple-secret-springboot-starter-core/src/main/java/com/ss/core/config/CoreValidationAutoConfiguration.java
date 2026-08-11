package com.ss.core.config;

import jakarta.validation.Validator;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Properties;

/** 可选 Bean Validation 快速失败自动配置。 */
@AutoConfiguration(after = SimpleSecretCoreAutoConfiguration.class)
@ConditionalOnClass(name = {
        "jakarta.validation.Validator",
        "org.hibernate.validator.HibernateValidator"
})
@ConditionalOnProperty(prefix = "simple-secret.core.validation", name = "fail-fast", havingValue = "true")
public class CoreValidationAutoConfiguration {

    /**
     * 创建由 Spring 管理生命周期的快速失败 Validator。
     *
     * @return Validator 工厂 Bean
     */
    @Bean
    @ConditionalOnMissingBean(Validator.class)
    public LocalValidatorFactoryBean simpleSecretValidator() {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setProviderClass(HibernateValidator.class);
        factoryBean.setMessageInterpolator(new ParameterMessageInterpolator());
        Properties validationProperties = new Properties();
        validationProperties.setProperty("hibernate.validator.fail_fast", "true");
        factoryBean.setValidationProperties(validationProperties);
        return factoryBean;
    }
}
