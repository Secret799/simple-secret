package com.ss.geo.math;

/**
 * 大地测量数学：WGS84 GPS ↔ ECEF 坐标转换、NED 基向量
 *
 * @author JunPzx
 * @since 2026/5/2
 */
public final class EarthMath {

    private EarthMath() {
    }

    /** WGS84 椭球长半轴（米） */
    public static final double WGS84_A = 6378137.0;

    /** WGS84 扁率 */
    public static final double WGS84_F = 1.0 / 298.257223563;

    /** WGS84 第一偏心率平方 */
    public static final double WGS84_E2 = 2.0 * WGS84_F - WGS84_F * WGS84_F;

    /** WGS84 椭球短半轴（米） */
    public static final double WGS84_B = WGS84_A * (1.0 - WGS84_F);

    /**
     * GPS 坐标转 ECEF
     *
     * @param lat 纬度（度）
     * @param lon 经度（度）
     * @param alt 海拔（米）
     * @return [x, y, z] ECEF 坐标（米）
     */
    public static double[] gpsToEcef(double lat, double lon, double alt) {
        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);
        double sinLat = Math.sin(latRad);
        double cosLat = Math.cos(latRad);

        double n = WGS84_A / Math.sqrt(1.0 - WGS84_E2 * sinLat * sinLat);

        double x = (n + alt) * cosLat * Math.cos(lonRad);
        double y = (n + alt) * cosLat * Math.sin(lonRad);
        double z = (n * (1.0 - WGS84_E2) + alt) * sinLat;

        return new double[]{x, y, z};
    }

    /**
     * ECEF 坐标转 GPS
     *
     * @param x ECEF X（米）
     * @param y ECEF Y（米）
     * @param z ECEF Z（米）
     * @return [lat, lon, alt] 纬度/经度（度）、海拔（米）
     */
    public static double[] ecefToGps(double x, double y, double z) {
        double lon = Math.toDegrees(Math.atan2(y, x));
        double p = Math.sqrt(x * x + y * y);
        if (p < 1.0e-9) {
            if (Math.abs(z) < 1.0e-9) {
                return new double[]{0, 0, -WGS84_A};
            }
            return new double[]{Math.copySign(90.0, z), 0, Math.abs(z) - WGS84_B};
        }
        double lat = Math.atan2(z, p * (1.0 - WGS84_E2));
        double n, h;

        // 迭代求解纬度和海拔
        for (int i = 0; i < 5; i++) {
            double sinLat = Math.sin(lat);
            n = WGS84_A / Math.sqrt(1.0 - WGS84_E2 * sinLat * sinLat);
            h = p / Math.cos(lat) - n;
            lat = Math.atan2(z, p * (1.0 - WGS84_E2 * n / (n + h)));
        }

        double sinLat = Math.sin(lat);
        n = WGS84_A / Math.sqrt(1.0 - WGS84_E2 * sinLat * sinLat);
        h = p / Math.cos(lat) - n;

        return new double[]{Math.toDegrees(lat), lon, h};
    }

    /**
     * 获取 NED 三轴在 ECEF 下的单位向量
     *
     * @param lat 纬度（度）
     * @param lon 经度（度）
     * @return [north, east, down] 各为 [x, y, z]
     */
    public static double[][] nedBasisVectors(double lat, double lon) {
        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);
        double sinLat = Math.sin(latRad);
        double cosLat = Math.cos(latRad);
        double sinLon = Math.sin(lonRad);
        double cosLon = Math.cos(lonRad);

        double[] north = {-sinLat * cosLon, -sinLat * sinLon, cosLat};
        double[] east = {-sinLon, cosLon, 0};
        double[] down = {-cosLat * cosLon, -cosLat * sinLon, -sinLat};

        return new double[][]{north, east, down};
    }

    /**
     * NED 向量转 ECEF 向量
     *
     * @param ned [north, east, down]
     * @param lat 纬度（度）
     * @param lon 经度（度）
     * @return [x, y, z] ECEF 单位向量
     */
    public static double[] nedToEcef(double[] ned, double lat, double lon) {
        double[][] basis = nedBasisVectors(lat, lon);
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            result[i] = ned[0] * basis[0][i] + ned[1] * basis[1][i] + ned[2] * basis[2][i];
        }
        return result;
    }
}
