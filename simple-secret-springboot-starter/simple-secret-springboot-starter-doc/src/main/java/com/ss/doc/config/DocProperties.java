package com.ss.doc.config;

import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Simple Secret OpenAPI 文档配置。 */
@ConfigurationProperties(prefix = "simple-secret.doc")
public class DocProperties {

    private boolean enabled;
    private boolean javadocTagsEnabled;
    private Info info = new Info();
    private Security security = new Security();

    /** 返回是否启用 doc 自动配置。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置是否启用 doc 自动配置。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 返回是否使用类 Javadoc 生成 Operation 标签。 */
    public boolean isJavadocTagsEnabled() {
        return javadocTagsEnabled;
    }

    /** 设置是否使用类 Javadoc 生成 Operation 标签。 */
    public void setJavadocTagsEnabled(boolean javadocTagsEnabled) {
        this.javadocTagsEnabled = javadocTagsEnabled;
    }

    /** 返回文档基本信息。 */
    public Info getInfo() {
        return info;
    }

    /** 设置文档基本信息。 */
    public void setInfo(Info info) {
        this.info = info == null ? new Info() : info;
    }

    /** 返回鉴权声明。 */
    public Security getSecurity() {
        return security;
    }

    /** 设置鉴权声明。 */
    public void setSecurity(Security security) {
        this.security = security == null ? new Security() : security;
    }

    /** OpenAPI Info 配置。 */
    public static class Info {

        private String title;
        private String description;
        private String version;
        private String termsOfService;
        private Contact contact = new Contact();
        private License license = new License();

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getTermsOfService() {
            return termsOfService;
        }

        public void setTermsOfService(String termsOfService) {
            this.termsOfService = termsOfService;
        }

        public Contact getContact() {
            return contact;
        }

        public void setContact(Contact contact) {
            this.contact = contact == null ? new Contact() : contact;
        }

        public License getLicense() {
            return license;
        }

        public void setLicense(License license) {
            this.license = license == null ? new License() : license;
        }
    }

    /** OpenAPI 联系人配置。 */
    public static class Contact {

        private String name;
        private String url;
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    /** OpenAPI 许可证配置。 */
    public static class License {

        private String name;
        private String url;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /** OpenAPI 鉴权配置。 */
    public static class Security {

        private Map<String, SecuritySchemeProperties> schemes = new LinkedHashMap<>();
        private Set<String> globallyRequired = new LinkedHashSet<>();

        public Map<String, SecuritySchemeProperties> getSchemes() {
            return schemes;
        }

        public void setSchemes(Map<String, SecuritySchemeProperties> schemes) {
            this.schemes = schemes == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(schemes);
        }

        public Set<String> getGloballyRequired() {
            return globallyRequired;
        }

        public void setGloballyRequired(Set<String> globallyRequired) {
            this.globallyRequired = globallyRequired == null
                    ? new LinkedHashSet<>() : new LinkedHashSet<>(globallyRequired);
        }
    }

    /** 单个 OpenAPI 鉴权方案配置。 */
    public static class SecuritySchemeProperties {

        private SecurityType type = SecurityType.API_KEY;
        private SecurityScheme.In location = SecurityScheme.In.HEADER;
        private String parameterName = "Authorization";
        private String bearerFormat;
        private String description;

        public SecurityType getType() {
            return type;
        }

        public void setType(SecurityType type) {
            this.type = type;
        }

        public SecurityScheme.In getLocation() {
            return location;
        }

        public void setLocation(SecurityScheme.In location) {
            this.location = location;
        }

        public String getParameterName() {
            return parameterName;
        }

        public void setParameterName(String parameterName) {
            this.parameterName = parameterName;
        }

        public String getBearerFormat() {
            return bearerFormat;
        }

        public void setBearerFormat(String bearerFormat) {
            this.bearerFormat = bearerFormat;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /** starter 支持的稳定鉴权方案类型。 */
    public enum SecurityType {
        API_KEY,
        HTTP_BASIC,
        HTTP_BEARER
    }
}
