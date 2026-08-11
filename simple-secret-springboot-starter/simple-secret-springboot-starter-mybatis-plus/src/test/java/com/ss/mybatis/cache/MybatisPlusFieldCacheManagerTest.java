package com.ss.mybatis.cache;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ss.common.toolbox.cache.ValueComparator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusFieldCacheManagerTest {
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "cache-test");
        assistant.setCurrentNamespace(DeviceService.class.getName());
        TableInfoHelper.initTableInfo(assistant, DeviceEntity.class);
    }

    @Test
    void updatesMappedColumnsWithKeyCondition() {
        AtomicReference<Wrapper<DeviceEntity>> captured = new AtomicReference<>();
        AtomicInteger updates = new AtomicInteger();
        DeviceService service = service(captured, updates, true);
        DeviceCacheManager manager = new DeviceCacheManager(service);

        manager.put("A-1", "online", true);

        assertThat(updates).hasValue(1);
        UpdateWrapper<DeviceEntity> wrapper = (UpdateWrapper<DeviceEntity>) captured.get();
        assertThat(wrapper.getSqlSet()).contains("online_state", "updated_at");
        assertThat(wrapper.getExpression().getNormal().getSqlSegment())
                .contains("device_id");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("A-1", "online", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    void statusManagerPublishesUpAndDownTransitions() {
        AtomicReference<Wrapper<DeviceEntity>> captured = new AtomicReference<>();
        DeviceStatusCacheManager manager = new DeviceStatusCacheManager(
                service(captured, new AtomicInteger(), true));
        AtomicReference<String> transition = new AtomicReference<>();
        manager.onUp(key -> transition.set("up:" + key));
        manager.onDown(key -> transition.set("down:" + key));

        manager.record("A-1", false);
        assertThat(manager.get("A-1")).isEqualTo("1");
        assertThat(transition).hasValue("up:A-1");

        manager.cancel("A-1", false);
        assertThat(manager.get("A-1")).isEqualTo("0");
        assertThat(transition).hasValue("down:A-1");
    }

    @SuppressWarnings("unchecked")
    private static DeviceService service(AtomicReference<Wrapper<DeviceEntity>> captured,
                                         AtomicInteger updates,
                                         boolean result) {
        return (DeviceService) Proxy.newProxyInstance(
                DeviceService.class.getClassLoader(),
                new Class<?>[]{DeviceService.class},
                (proxy, method, args) -> {
                    if ("update".equals(method.getName()) && args != null
                            && args.length == 1 && args[0] instanceof Wrapper<?>) {
                        captured.set((Wrapper<DeviceEntity>) args[0]);
                        updates.incrementAndGet();
                        return result;
                    }
                    if ("toString".equals(method.getName())) {
                        return "DeviceServiceProxy";
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }

    private static final class DeviceCacheManager extends
            MybatisPlusFieldCacheManager<DeviceEntity, DeviceService, String, String> {
        private final DeviceService service;

        private DeviceCacheManager(DeviceService service) {
            super(DeviceEntity.class, Duration.ofMinutes(1),
                    ValueComparator.natural(), Clock.fixed(NOW, ZoneOffset.UTC));
            this.service = service;
        }

        @Override
        protected DeviceService service() {
            return service;
        }

        @Override
        protected SFunction<DeviceEntity, ?> keyField() {
            return DeviceEntity::getId;
        }

        @Override
        protected SFunction<DeviceEntity, ?> valueField() {
            return DeviceEntity::getStatus;
        }

        @Override
        protected SFunction<DeviceEntity, LocalDateTime> updateTimeField() {
            return DeviceEntity::getUpdateTime;
        }
    }

    private static final class DeviceStatusCacheManager extends
            MybatisPlusStatusFieldCacheManager<DeviceEntity, DeviceService> {
        private final DeviceService service;

        private DeviceStatusCacheManager(DeviceService service) {
            super(DeviceEntity.class, Duration.ofMinutes(1));
            this.service = service;
        }

        @Override
        protected DeviceService service() {
            return service;
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

    @TableName("device")
    private static final class DeviceEntity {
        @TableId("device_id")
        private String id;
        @TableField("online_state")
        private String status;
        @TableField("updated_at")
        private LocalDateTime updateTime;

        public String getId() {
            return id;
        }

        public String getStatus() {
            return status;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }
    }
}
