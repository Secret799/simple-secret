package com.ss.camera.service;

import com.ss.camera.domain.StreamUrlAssemblyDomain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 按品牌和设备类型查找 RTSP 地址组装器。
 *
 * <p>注册表在构造后不可变，可由多个线程安全共享。</p>
 */
public final class UrlAssemblyHolder {
    private final Map<Key, UrlAssemblyService> services;

    /**
     * 创建组装器注册表。
     *
     * @param services 可用组装器
     * @throws IllegalArgumentException 组装器为空或品牌、类型组合重复时抛出
     */
    public UrlAssemblyHolder(Collection<? extends UrlAssemblyService> services) {
        if (services == null) {
            throw new IllegalArgumentException("services must not be null");
        }
        Map<Key, UrlAssemblyService> index = new LinkedHashMap<>();
        for (UrlAssemblyService service : services) {
            if (service == null) {
                throw new IllegalArgumentException("service must not be null");
            }
            Key key = key(service.brand(), service.type());
            UrlAssemblyService existing = index.putIfAbsent(key, service);
            if (existing != null) {
                throw new IllegalArgumentException("Duplicate URL assembly service for "
                        + service.brand() + ":" + service.type());
            }
        }
        this.services = Map.copyOf(index);
    }

    /**
     * 组装指定设备的 RTSP 地址。
     *
     * @param domain 地址参数
     * @return 完整 RTSP 地址
     * @throws IllegalArgumentException 未注册对应组装器时抛出
     */
    public String assembly(StreamUrlAssemblyDomain domain) {
        if (domain == null) {
            throw new IllegalArgumentException("domain must not be null");
        }
        UrlAssemblyService service = get(domain.getBrand(), domain.getType());
        if (service == null) {
            throw new IllegalArgumentException("No URL assembly service for "
                    + String.valueOf(domain.getBrand()) + ":" + String.valueOf(domain.getType()));
        }
        return service.assembly(domain);
    }

    /**
     * 查找组装器，品牌和类型不区分大小写。
     *
     * @param brand 品牌编码
     * @param type 设备类型编码
     * @return 已注册的组装器；不存在时返回 {@code null}
     */
    public UrlAssemblyService get(String brand, String type) {
        if (brand == null || brand.isBlank() || type == null || type.isBlank()) {
            return null;
        }
        return services.get(key(brand, type));
    }

    private static Key key(String brand, String type) {
        if (brand == null || brand.isBlank() || type == null || type.isBlank()) {
            throw new IllegalArgumentException("service brand and type must not be blank");
        }
        return new Key(brand.trim().toUpperCase(Locale.ROOT), type.trim().toUpperCase(Locale.ROOT));
    }

    private record Key(String brand, String type) {
    }
}
