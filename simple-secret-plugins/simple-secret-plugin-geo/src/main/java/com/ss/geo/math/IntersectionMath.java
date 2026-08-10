package com.ss.geo.math;

/**
 * 射线-地形求交：ECEF 射线与指定海拔的 WGS84 椭球面求交点
 *
 * @author JunPzx
 * @since 2026/5/2
 */
public final class IntersectionMath {

    private IntersectionMath() {
    }

    /** 收敛阈值（米） */
    private static final double EPSILON = 0.01;

    /** 最大迭代次数 */
    private static final int MAX_ITERATIONS = 30;

    /** 最大求交距离（米） */
    private static final double MAX_DISTANCE = 100000.0;

    /**
     * ECEF 射线与指定海拔面求交
     *
     * @param camEcef        相机 ECEF 位置 [x, y, z]
     * @param rayDirection   射线 ECEF 方向 [x, y, z]（单位向量）
     * @param groundAltitude 目标地面海拔（米，WGS84）
     * @return 交点 ECEF 坐标 [x, y, z]
     */
    public static double[] intersect(double[] camEcef, double[] rayDirection, double groundAltitude) {
        // 相机 GPS
        double[] camGps = EarthMath.ecefToGps(camEcef[0], camEcef[1], camEcef[2]);

        // 相机处的 NED 基向量
        double[][] nedBasis = EarthMath.nedBasisVectors(camGps[0], camGps[1]);

        // 射线 NED 分量：点积投影
        double dDown = dot(rayDirection, nedBasis[2]);

        // 必须向下看才可能命中地面（NED Down 分量指向地心，正值=向下）
        if (dDown <= 0) {
            // 射线水平或向上，未指向地面
            return null;
        }

        // 初始 t 估计：平坦地球近似
        double dh = camGps[2] - groundAltitude;
        double t = dh / Math.abs(dDown);

        if (t <= 0 || t > MAX_DISTANCE) {
            return null;
        }

        // 迭代修正（考虑地球曲率和椭球效应）
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // 当前试探点
            double px = camEcef[0] + t * rayDirection[0];
            double py = camEcef[1] + t * rayDirection[1];
            double pz = camEcef[2] + t * rayDirection[2];

            // 转换为 GPS 查看海拔
            double[] pointGps = EarthMath.ecefToGps(px, py, pz);
            double altError = pointGps[2] - groundAltitude;

            if (Math.abs(altError) < EPSILON) {
                return new double[]{px, py, pz};
            }

            // 使用当前点的局部 Down 方向计算海拔导数，近地平线时也能稳定收敛。
            double[][] pointNedBasis = EarthMath.nedBasisVectors(pointGps[0], pointGps[1]);
            double currentDown = dot(rayDirection, pointNedBasis[2]);
            if (!Double.isFinite(currentDown) || currentDown <= 0) {
                return null;
            }
            t = t + altError / currentDown;

            if (!Double.isFinite(t) || t < 0 || t > MAX_DISTANCE) {
                return null;
            }
        }

        // 达到最大迭代次数后必须再次验证，不能把未收敛点当作有效交点。
        double px = camEcef[0] + t * rayDirection[0];
        double py = camEcef[1] + t * rayDirection[1];
        double pz = camEcef[2] + t * rayDirection[2];
        double altitudeError = EarthMath.ecefToGps(px, py, pz)[2] - groundAltitude;
        return Math.abs(altitudeError) < EPSILON ? new double[]{px, py, pz} : null;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
