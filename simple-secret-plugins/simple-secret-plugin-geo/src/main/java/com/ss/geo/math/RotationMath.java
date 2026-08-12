package com.ss.geo.math;

/**
 * DJI Pan-Tilt-Roll 云台姿态矩阵及矩阵-向量乘法。
 *
 * @author JunPzx
 * @since 2026/5/2
 */
public final class RotationMath {

    private RotationMath() {
    }

    /**
     * 构建 NED 到计算机视觉相机坐标系的正交基矩阵。
     *
     * <p>方法名为兼容现有调用保留；DJI 绝对云台角实际按物理 Pan-Tilt-Roll
     * 建模，而不是通用 ZYX 欧拉角。返回矩阵三行依次为相机 right、down、look
     * 基向量，均使用 NED 分量表示。
     *
     * @param yaw   绝对偏航角（度，0=北，顺时针为正）
     * @param pitch 绝对俯仰角（度，负值向下）
     * @param roll  绕视线轴的横滚角（度，正值右倾）
     * @return NED 到 CV 相机坐标系的 3×3 正交矩阵
     */
    public static double[][] fromEulerZYX(double yaw, double pitch, double roll) {
        double cosY = Math.cos(Math.toRadians(yaw));
        double sinY = Math.sin(Math.toRadians(yaw));
        double cosP = Math.cos(Math.toRadians(pitch));
        double sinP = Math.sin(Math.toRadians(pitch));
        double cosR = Math.cos(Math.toRadians(roll));
        double sinR = Math.sin(Math.toRadians(roll));

        double[] right0 = {-sinY, cosY, 0.0};
        double[] look = {cosP * cosY, cosP * sinY, -sinP};
        double[] up0 = {-cosY * sinP, -sinY * sinP, -cosP};

        double[] right = {
                cosR * right0[0] + sinR * up0[0],
                cosR * right0[1] + sinR * up0[1],
                cosR * right0[2] + sinR * up0[2]
        };
        double[] up = {
                -sinR * right0[0] + cosR * up0[0],
                -sinR * right0[1] + cosR * up0[1],
                -sinR * right0[2] + cosR * up0[2]
        };
        double[] down = {-up[0], -up[1], -up[2]};

        return new double[][]{right, down, look};
    }

    /**
     * 矩阵 × 列向量
     *
     * @param m 3×3 矩阵
     * @param v 3 维向量
     * @return m * v
     */
    public static double[] multiply(double[][] m, double[] v) {
        return new double[]{
                m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]
        };
    }

    /**
     * 矩阵转置 × 列向量（等价于逆旋转，因为旋转矩阵正交）
     *
     * @param m 3×3 旋转矩阵
     * @param v 3 维向量
     * @return m^T * v
     */
    public static double[] multiplyTranspose(double[][] m, double[] v) {
        return new double[]{
                m[0][0] * v[0] + m[1][0] * v[1] + m[2][0] * v[2],
                m[0][1] * v[0] + m[1][1] * v[1] + m[2][1] * v[2],
                m[0][2] * v[0] + m[1][2] * v[1] + m[2][2] * v[2]
        };
    }

    /**
     * 向量归一化（原地修改）

     *
     * @param v 三维向量
     */
    public static void normalize(double[] v) {
        double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len > 0) {
            v[0] /= len;
            v[1] /= len;
            v[2] /= len;
        }
    }
}
