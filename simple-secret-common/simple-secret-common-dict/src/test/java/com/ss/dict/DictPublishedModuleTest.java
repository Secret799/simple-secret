package com.ss.dict;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 dict 模块的发布坐标和最小生产依赖边界。 */
class DictPublishedModuleTest {

    @Test
    void publishesDictArtifactThroughRootManagementAndBom() throws Exception {
        String modulePom = Files.readString(Path.of("pom.xml"));
        String rootPom = Files.readString(Path.of("../../pom.xml"));
        String bomPom = Files.readString(Path.of("../simple-secret-common-bom/pom.xml"));

        assertTrue(modulePom.contains("simple-secret-common-toolbox"));
        assertTrue(rootPom.contains("simple-secret-common-dict"));
        assertTrue(bomPom.contains("simple-secret-common-dict"));

        assertFalse(modulePom.contains("spring-"));
        assertFalse(modulePom.contains("hutool"));
        assertFalse(modulePom.contains("lombok"));
        assertFalse(modulePom.contains("jackson"));
        assertFalse(modulePom.contains("mybatis"));
        assertFalse(modulePom.contains("redis"));
        assertFalse(modulePom.contains("com.secret"));
        assertFalse(modulePom.contains("simple-secret-springboot-starter"));
    }

    @Test
    void publishesThirdPartyUsageAndFailureSemantics() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertTrue(readme.contains("simple-secret-common-dict"));
        assertTrue(readme.contains("DictionaryRegistry"));
        assertTrue(readme.contains("registerEnum"));
        assertTrue(readme.contains("@DictField"));
        assertTrue(readme.contains("invalidate"));
        assertTrue(readme.contains("DictionaryMappingException"));
        assertFalse(readme.contains("com.secret"));
    }
}
