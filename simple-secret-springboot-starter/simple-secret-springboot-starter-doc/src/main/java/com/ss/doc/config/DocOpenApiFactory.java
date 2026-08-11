package com.ss.doc.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.LinkedHashMap;
import java.util.Map;

/** 将公共配置转换为 OpenAPI 模型。 */
final class DocOpenApiFactory {

    private DocOpenApiFactory() {
    }

    static OpenAPI create(DocProperties properties) {
        OpenAPI openApi = new OpenAPI();
        Info info = createInfo(properties.getInfo());
        if (info != null) {
            openApi.setInfo(info);
        }

        Map<String, SecurityScheme> schemes = createSecuritySchemes(properties.getSecurity());
        if (!schemes.isEmpty()) {
            openApi.setComponents(new Components().securitySchemes(schemes));
        }
        addGlobalSecurity(openApi, properties.getSecurity(), schemes);
        return openApi;
    }

    private static Info createInfo(DocProperties.Info source) {
        Contact contact = createContact(source.getContact());
        License license = createLicense(source.getLicense());
        String title = trimToNull(source.getTitle());
        String description = trimToNull(source.getDescription());
        String version = trimToNull(source.getVersion());
        String termsOfService = trimToNull(source.getTermsOfService());
        if (title == null && description == null && version == null
                && termsOfService == null && contact == null && license == null) {
            return null;
        }
        return new Info()
                .title(title)
                .description(description)
                .version(version)
                .termsOfService(termsOfService)
                .contact(contact)
                .license(license);
    }

    private static Contact createContact(DocProperties.Contact source) {
        String name = trimToNull(source.getName());
        String url = trimToNull(source.getUrl());
        String email = trimToNull(source.getEmail());
        if (name == null && url == null && email == null) {
            return null;
        }
        return new Contact().name(name).url(url).email(email);
    }

    private static License createLicense(DocProperties.License source) {
        String name = trimToNull(source.getName());
        String url = trimToNull(source.getUrl());
        if (name == null && url == null) {
            return null;
        }
        return new License().name(name).url(url);
    }

    private static Map<String, SecurityScheme> createSecuritySchemes(
            DocProperties.Security security) {
        Map<String, SecurityScheme> result = new LinkedHashMap<>();
        security.getSchemes().forEach((name, properties) -> {
            if (trimToNull(name) == null) {
                throw invalid("simple-secret.doc.security.schemes", "scheme name must not be blank");
            }
            if (properties == null || properties.getType() == null) {
                throw invalid("simple-secret.doc.security.schemes." + name + ".type",
                        "type is required");
            }
            result.put(name, createSecurityScheme(name, properties));
        });
        return result;
    }

    private static SecurityScheme createSecurityScheme(
            String name, DocProperties.SecuritySchemeProperties properties) {
        SecurityScheme scheme = new SecurityScheme()
                .description(trimToNull(properties.getDescription()));
        return switch (properties.getType()) {
            case API_KEY -> createApiKeyScheme(name, properties, scheme);
            case HTTP_BASIC -> scheme.type(SecurityScheme.Type.HTTP).scheme("basic");
            case HTTP_BEARER -> scheme.type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat(trimToNull(properties.getBearerFormat()));
        };
    }

    private static SecurityScheme createApiKeyScheme(
            String name,
            DocProperties.SecuritySchemeProperties properties,
            SecurityScheme scheme) {
        if (properties.getLocation() == null) {
            throw invalid("simple-secret.doc.security.schemes." + name + ".location",
                    "location is required");
        }
        String parameterName = trimToNull(properties.getParameterName());
        if (parameterName == null) {
            throw invalid("simple-secret.doc.security.schemes." + name + ".parameter-name",
                    "parameter name must not be blank");
        }
        return scheme.type(SecurityScheme.Type.APIKEY)
                .in(properties.getLocation())
                .name(parameterName);
    }

    private static void addGlobalSecurity(
            OpenAPI openApi,
            DocProperties.Security security,
            Map<String, SecurityScheme> schemes) {
        if (security.getGloballyRequired().isEmpty()) {
            return;
        }
        SecurityRequirement requirement = new SecurityRequirement();
        for (String name : security.getGloballyRequired()) {
            if (!schemes.containsKey(name)) {
                throw invalid("simple-secret.doc.security.globally-required",
                        "unknown scheme '" + name + "'");
            }
            requirement.addList(name);
        }
        openApi.addSecurityItem(requirement);
    }

    private static IllegalArgumentException invalid(String property, String reason) {
        return new IllegalArgumentException("Invalid configuration property '"
                + property + "': " + reason);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
