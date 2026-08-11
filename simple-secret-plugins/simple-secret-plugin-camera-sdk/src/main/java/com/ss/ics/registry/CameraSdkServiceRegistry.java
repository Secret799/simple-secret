package com.ss.ics.registry;

import com.ss.ics.exception.UnsupportedCameraSdkOperationException;
import com.ss.ics.service.CameraSdkService;
import com.ss.ics.service.DeviceLoginService;
import com.ss.ics.service.PlayQueryService;
import com.ss.ics.service.PlayService;
import com.ss.ics.service.PtzControlService;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 按厂商产品编码索引 SDK 能力的不可变注册表。
 *
 * <p>注册表不扫描 classpath，也不保存 Spring 全局状态。</p>
 */
public final class CameraSdkServiceRegistry {
    private final Map<String, PtzControlService> ptzServices;
    private final Map<String, PlayService<?, ?>> playServices;
    private final Map<String, DeviceLoginService> loginServices;
    private final Map<String, PlayQueryService> playQueryServices;

    /**
     * @param services 调用方显式提供的厂商能力
     */
    public CameraSdkServiceRegistry(Collection<? extends CameraSdkService> services) {
        if (services == null) {
            throw new IllegalArgumentException("services must not be null");
        }
        Map<String, PtzControlService> ptzIndex = new LinkedHashMap<>();
        Map<String, PlayService<?, ?>> playIndex = new LinkedHashMap<>();
        Map<String, DeviceLoginService> loginIndex = new LinkedHashMap<>();
        Map<String, PlayQueryService> playQueryIndex = new LinkedHashMap<>();
        for (CameraSdkService service : services) {
            if (service == null) {
                throw new IllegalArgumentException("service must not be null");
            }
            if (service instanceof PtzControlService ptz) {
                put(ptzIndex, ptz, "PTZ");
            }
            if (service instanceof PlayService<?, ?> play) {
                put(playIndex, play, "play");
            }
            if (service instanceof DeviceLoginService login) {
                put(loginIndex, login, "login");
            }
            if (service instanceof PlayQueryService playQuery) {
                put(playQueryIndex, playQuery, "play query");
            }
        }
        this.ptzServices = Map.copyOf(ptzIndex);
        this.playServices = Map.copyOf(playIndex);
        this.loginServices = Map.copyOf(loginIndex);
        this.playQueryServices = Map.copyOf(playQueryIndex);
    }

    /** @param product 厂商产品编码 @return 对应 PTZ 服务 */
    public Optional<PtzControlService> findPtz(String product) {
        return Optional.ofNullable(ptzServices.get(key(product)));
    }

    /** @param product 厂商产品编码 @return 对应 PTZ 服务 */
    public PtzControlService requirePtz(String product) {
        return findPtz(product).orElseThrow(() -> unsupported("PTZ"));
    }

    /**
     * @param product 厂商产品编码
     * @param serviceType 播放服务实现类型
     * @param <S> 播放服务实现类型
     * @return 对应播放服务
     */
    public <S extends PlayService<?, ?>> Optional<S> findPlay(String product, Class<S> serviceType) {
        return Optional.ofNullable(playServices.get(key(product))).map(serviceType::cast);
    }

    /**
     * @param product 厂商产品编码
     * @param serviceType 播放服务实现类型
     * @param <S> 播放服务实现类型
     * @return 对应播放服务
     */
    public <S extends PlayService<?, ?>> S requirePlay(String product, Class<S> serviceType) {
        return findPlay(product, serviceType).orElseThrow(() -> unsupported("play"));
    }

    /** @param product 厂商产品编码 @return 对应登录服务 */
    public Optional<DeviceLoginService> findLogin(String product) {
        return Optional.ofNullable(loginServices.get(key(product)));
    }

    /** @param product 厂商产品编码 @return 对应登录服务 */
    public DeviceLoginService requireLogin(String product) {
        return findLogin(product).orElseThrow(() -> unsupported("login"));
    }

    /** @param product 厂商产品编码 @return 对应录像查询服务 */
    public Optional<PlayQueryService> findPlayQuery(String product) {
        return Optional.ofNullable(playQueryServices.get(key(product)));
    }

    /** @param product 厂商产品编码 @return 对应录像查询服务 */
    public PlayQueryService requirePlayQuery(String product) {
        return findPlayQuery(product).orElseThrow(() -> unsupported("play query"));
    }

    private static <S extends CameraSdkService> void put(Map<String, S> target, S service, String capability) {
        String product = service.product();
        String key = key(product);
        if (target.putIfAbsent(key, service) != null) {
            throw new IllegalArgumentException("Duplicate " + capability
                    + " camera SDK service for " + product);
        }
    }

    private static String key(String product) {
        if (product == null || product.isBlank()) {
            throw new IllegalArgumentException("product must not be blank");
        }
        return product.trim().toUpperCase(Locale.ROOT);
    }

    private static UnsupportedCameraSdkOperationException unsupported(String capability) {
        return new UnsupportedCameraSdkOperationException(
                "No " + capability + " camera SDK service is registered");
    }
}
