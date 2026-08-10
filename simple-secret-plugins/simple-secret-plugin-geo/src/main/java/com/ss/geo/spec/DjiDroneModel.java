package com.ss.geo.spec;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 大疆无人机型号相机规格表（含机场三代飞机 + 常用便携机）
 *
 * <p>每个枚举值代表一种无人机型号，包含该机型的所有相机规格。
 * 基准 FOV 对应相机规格的最小绝对倍率，通过 {@link CameraSpec#diagonalFovAtZoom(double)}
 * 或 {@link CameraSpec#fovHvAtZoom(double)} 可根据 DJI 实时绝对倍率计算当前 FOV。
 *
 * <pre>
 * 使用示例：
 *   DjiDroneModel model = DjiDroneModel.M3TD;
 *   CameraSpec wide = model.getSpec(CameraType.WIDE);        // 广角规格
 *   CameraSpec zoom = model.getSpec(CameraType.ZOOM);        // 变焦规格
 *   double currentFov = zoom.diagonalFovAtZoom(7.0);         // 7x 变焦时的对角线 FOV
 *   double[] fovHv = zoom.fovHvAtZoom(7.0);                  // [水平FOV, 垂直FOV]
 * </pre>
 *
 * @author JunPzx
 * @since 2026/5/11
 */
public enum DjiDroneModel {

    // === 一代机场 (DJI Dock) ===

    /**
     * Matrice 30 — 一代机场，无热成像
     */
    M30("Matrice 30", "一代机场", "0-67-0",
            new CameraSpec(CameraType.WIDE, 84.0, 24, 6.4, 4.8,
                    4000, 3000, "2.8", false, 0, 0),
            new CameraSpec(CameraType.ZOOM, 21.7, 113, 6.4, 4.8,
                    8000, 6000, "2.8", false, 5, 200)
    ),

    /**
     * Matrice 30T — 一代机场，带热成像
     */
    M30T("Matrice 30T", "一代机场", "0-67-1",
            new CameraSpec(CameraType.WIDE, 84.0, 24, 6.4, 4.8,
                    4000, 3000, "2.8", false, 0, 0),
            new CameraSpec(CameraType.ZOOM, 21.7, 113, 6.4, 4.8,
                    8000, 6000, "2.8", false, 5, 200),
            new CameraSpec(CameraType.THERMAL, 61.0, 40, 7.68, 6.14,
                    640, 512, "1.0", false, 0, 28)
    ),

    // === 二代机场 (DJI Dock 2) ===

    /**
     * Matrice 3D — 二代机场，测绘版
     */
    M3D("Matrice 3D", "二代机场", "0-91-0",
            new CameraSpec(CameraType.WIDE, 84.0, 24, 17.3, 13.0,
                    5280, 3956, "2.8-11", true, 0, 0),
            new CameraSpec(CameraType.ZOOM, 15.0, 162, 6.4, 4.8,
                    4000, 3000, "4.4", false, 1, 200)
    ),

    /**
     * Matrice 3TD — 二代机场，热成像版
     */
    M3TD("Matrice 3TD", "二代机场", "0-91-1",
            new CameraSpec(CameraType.WIDE, 82.0, 24, 9.6, 7.2,
                    8064, 6048, "1.7", false, 0, 0),
            new CameraSpec(CameraType.ZOOM, 15.0, 162, 6.4, 4.8,
                    4000, 3000, "4.4", false, 1, 56),
            new CameraSpec(CameraType.THERMAL, 61.0, 40, 7.68, 6.14,
                    640, 512, "1.0", false, 0, 28)
    ),

    // === 三代机场 (DJI Dock 3) ===

    /**
     * Matrice 4D — 三代机场，测绘版（Wide + Medium Tele + Tele 三摄）
     */
    M4D("Matrice 4D", "三代机场", "0-100-0",
            new CameraSpec(CameraType.WIDE, 84.0, 24, 17.3, 13.0,
                    5280, 3956, "2.8-11", true, 0, 0),
            new CameraSpec(CameraType.MEDIUM_TELE, 34.3, 70, 9.6, 7.2,
                    8192, 6144, "2.8", false, 0, 0),
            new CameraSpec(CameraType.ZOOM, 14.7, 168, 6.4, 4.8,
                    8192, 6144, "2.8", false, 1, 112)
    ),

    /**
     * Matrice 4TD — 三代机场，热成像版（Wide + Medium Tele + Tele + Thermal 四摄）
     */
    M4TD("Matrice 4TD", "三代机场", "0-100-1",
            new CameraSpec(CameraType.WIDE, 82.0, 24, 9.6, 7.2,
                    8064, 6048, "1.7", false, 0, 0),
            new CameraSpec(CameraType.MEDIUM_TELE, 34.3, 70, 9.6, 7.2,
                    8192, 6144, "2.8", false, 0, 0),
            new CameraSpec(CameraType.ZOOM, 14.7, 168, 6.4, 4.8,
                    8192, 6144, "2.8", false, 1, 112),
            new CameraSpec(CameraType.THERMAL, 44.4, 53, 7.68, 6.14,
                    640, 512, "1.0", false, 0, 0)
    ),

    // === 便携机（非机场） ===

    /**
     * Mavic 3E — 便携测绘机
     */
    M3E("Mavic 3E", "便携机", "0-77-0",
            new CameraSpec(CameraType.WIDE, 84.0, 24, 17.3, 13.0,
                    5280, 3956, "2.8-11", true, 0, 0),
            new CameraSpec(CameraType.ZOOM, 15.0, 162, 6.4, 4.8,
                    4000, 3000, "4.4", false, 1, 56)
    ),

    /**
     * Mavic 3T — 便携热成像机
     */
    M3T("Mavic 3T", "便携机", "0-77-1",
            new CameraSpec(CameraType.WIDE, 84.0, 24, 9.6, 7.2,
                    8000, 6000, "1.7", false, 0, 0),
            new CameraSpec(CameraType.ZOOM, 15.0, 162, 6.4, 4.8,
                    4000, 3000, "4.4", false, 1, 56),
            new CameraSpec(CameraType.THERMAL, 61.0, 40, 7.68, 6.14,
                    640, 512, "1.0", false, 0, 0)
    ),

    // === 大飞机（挂载 H30/H30T 系列负载） ===

    /**
     * Matrice 350 RTK（挂载 Zenmuse H30/H30T 负载）。
     * 变焦相机光学变焦范围 7.1mm–172mm（物理），等效约 38mm–929mm。
     * 基准 FOV 取变焦广角端（7.1mm 物理焦距时）。
     */
    M350("Matrice 350 RTK", "大飞机", "0-89-0",
            new CameraSpec(CameraType.WIDE, 82.1, 24, 9.6, 7.2,
                    8064, 6048, "1.7", false, 0, 0),
            new CameraSpec(CameraType.ZOOM, 58.8, 38, 6.4, 4.8,
                    7328, 5496, "5.2", false, 1, 56),
            new CameraSpec(CameraType.THERMAL, 61.0, 40, 7.68, 6.14,
                    640, 512, "1.0", false, 0, 0)
    );

    /**
     * 显示名称
     */
    private final String displayName;

    /**
     * 所属代际
     */
    private final String generation;

    /**
     * 产品类型编码，格式: domain-type-sub_type
     */
    private final String typeCode;

    /**
     * 该机型的所有相机，按类型索引
     */
    private final Map<CameraType, CameraSpec> cameras;

    DjiDroneModel(String displayName, String generation, String typeCode, CameraSpec... specs) {
        this.displayName = displayName;
        this.generation = generation;
        this.typeCode = typeCode;
        Map<CameraType, CameraSpec> map = new EnumMap<>(CameraType.class);
        for (CameraSpec spec : specs) {
            map.put(spec.type(), spec);
        }
        this.cameras = Collections.unmodifiableMap(map);
    }

    /**
     * 返回机型显示名称。
     *
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 返回机型所属产品代际。
     *
     * @return 产品代际
     */
    public String getGeneration() {
        return generation;
    }

    /**
     * 返回 DJI 产品类型编码。
     *
     * @return 产品类型编码
     */
    public String getTypeCode() {
        return typeCode;
    }

    /**
     * 获取指定类型的相机规格
     *
     * @param type 相机类型
     * @return 相机规格，如果该机型无此类型相机则返回 null
     */
    public CameraSpec getSpec(CameraType type) {
        return cameras.get(type);
    }

    /**
     * 获取该机型所有可用的相机类型
     *
     * @return 相机类型集合
     */
    public Set<CameraType> getAvailableCameras() {
        return cameras.keySet();
    }

    /**
     * 该机型是否有指定类型的相机
     *
     * @param type 相机类型
     * @return true 表示有
     */
    public boolean hasCamera(CameraType type) {
        return cameras.containsKey(type);
    }

    /**
     * 根据产品类型编码查找对应的无人机型号
     *
     * @param typeCode 产品类型编码，格式: domain-type-sub_type，例如 "0-91-0"
     * @return 对应的无人机型号枚举值
     * @throws IllegalArgumentException 如果编码未匹配到任何型号
     */
    public static DjiDroneModel getByTypeCode(String typeCode) {
        for (DjiDroneModel model : values()) {
            if (model.typeCode.equals(typeCode)) {
                return model;
            }
        }
        throw new IllegalArgumentException("Unknown typeCode: " + typeCode);
    }

    /**
     * 根据常见 EXIF/XMP 型号名称识别机型。
     *
     * @param name 型号或产品名称
     * @return 已识别机型，无法识别时返回 null
     */
    public static DjiDroneModel findByName(String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) {
            return null;
        }
        for (DjiDroneModel model : values()) {
            if (normalized.equals(normalizeName(model.name()))
                    || normalized.equals(normalizeName(model.displayName))
                    || normalized.equals(normalizeName("DJI " + model.name()))
                    || normalized.equals(normalizeName("DJI " + model.displayName))) {
                return model;
            }
        }
        return switch (normalized) {
            case "M3TD", "DJIM3TD" -> M3TD;
            case "M4TD", "DJIM4TD" -> M4TD;
            case "M4D", "DJIM4D" -> M4D;
            case "M3E", "DJIM3E", "MAVIC3ENTERPRISE", "DJIMAVIC3ENTERPRISE" -> M3E;
            case "M3T", "DJIM3T" -> M3T;
            case "M350", "M350RTK", "DJIM350", "DJIM350RTK" -> M350;
            default -> null;
        };
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
