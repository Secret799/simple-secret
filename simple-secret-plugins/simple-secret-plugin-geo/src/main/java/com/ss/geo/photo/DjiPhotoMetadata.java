package com.ss.geo.photo;

import java.io.Serial;
import java.io.Serializable;

/**
 * DJI 照片元数据（从 EXIF + XMP 直接读取，不做计算）
 *
 * @author JunPzx
 * @since 2026/5/2
 */
public class DjiPhotoMetadata implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // === 基础 EXIF 信息（IFD0） ===

    /** 制造商 */
    private String make;

    /** 型号 */
    private String model;

    /** 固件版本 */
    private String software;

    /** 修改日期 */
    private String modifyDate;

    /** 图像描述 */
    private String imageDescription;

    // === 拍摄参数（ExifIFD） ===

    /** 曝光时间（秒） */
    private Double exposureTime;

    /** 光圈值 */
    private Double fNumber;

    /** ISO 感光度 */
    private Integer iso;

    /** 原始拍摄日期时间 */
    private String dateTimeOriginal;

    /** 快门类型（XMP） */
    private String shutterType;

    /** 传感器帧率（XMP） */
    private Double sensorFPS;

    /** 白平衡色温（XMP） */
    private Integer whiteBalanceCCT;

    /** 传感器温度（XMP） */
    private Double sensorTemperature;

    // === 焦距相关（ExifIFD，直接来自图片，不做计算） ===

    /** 物理焦距（mm） */
    private Double focalLength;

    /** 焦平面水平分辨率 */
    private Double focalPlaneXResolution;

    /** 焦平面垂直分辨率 */
    private Double focalPlaneYResolution;

    /** 焦平面分辨率单位（2=inch, 3=cm） */
    private Integer focalPlaneResolutionUnit;

    /** 35mm 等效焦距（直接来自 EXIF） */
    private Double focalLength35mm;

    /** 标定焦距（像素；仅来自显式 DJI XMP {@code drone-dji:CalibratedFocalLength}，不解析 MakerNote） */
    private Double calibratedFocalLength;

    // === 设备标识 ===

    /** 机身序列号（ExifIFD） */
    private String bodySerialNumber;

    /** 镜头规格（ExifIFD） */
    private String lensSpecification;

    /** 唯一相机型号（ExifIFD） */
    private String uniqueCameraModel;

    /** 产品名称（XMP） */
    private String productName;

    /** 无人机型号（XMP） */
    private String droneModel;

    /** 无人机序列号（XMP） */
    private String droneSerialNumber;

    /** 相机序列号（XMP） */
    private String cameraSerialNumber;

    // === DJI XMP 版本与来源 ===

    /** DJI XMP 版本 */
    private String djiVersion;

    /** 图像来源（WideCamera / ZoomCamera 等） */
    private String imageSource;

    /** 当前变焦倍率（如元数据提供） */
    private Double zoomFactor;

    /** 数字变焦/裁切倍率（EXIF DigitalZoomRatio） */
    private Double digitalZoomRatio;

    /** 产品类型编码，格式: domain-type-sub_type */
    private String droneTypeCode;

    // === GPS 状态 ===

    /** GPS 状态（RTK / GPS 等） */
    private String gpsStatus;

    /** 海拔类型（RtkAlt / PressureAlt 等） */
    private String altitudeType;

    // === RTK 精度 ===

    /** RTK 标志 */
    private Integer rtkFlag;

    /** RTK 经度标准差 */
    private Double rtkStdLon;

    /** RTK 纬度标准差 */
    private Double rtkStdLat;

    /** RTK 高度标准差 */
    private Double rtkStdHgt;

    /** RTK 差分龄期 */
    private Double rtkDiffAge;

    // === 飞行速度 ===

    /** X 方向飞行速度（m/s，XMP） */
    private Double flightXSpeed;

    /** Y 方向飞行速度（m/s，XMP） */
    private Double flightYSpeed;

    /** Z 方向飞行速度（m/s，XMP） */
    private Double flightZSpeed;

    // === 反向标志 ===

    /** 相机反向 */
    private Integer camReverse;

    /** 云台反向 */
    private Integer gimbalReverse;

    // === 测量模式 ===

    /** 测量模式 */
    private Integer surveyingMode;

    // === 时间 ===

    /** 曝光时刻 UTC 时间（XMP，微秒精度） */
    private String utcAtExposure;

    // === 自定义数据 ===

    /** 自定义数据（XMP） */
    private String selfData;

    // === 定位（EXIF GPS + XMP） ===

    /** 纬度（度，WGS84） */
    private Double gpsLat;

    /** 经度（度，WGS84） */
    private Double gpsLon;

    /** GPS 海拔（米） */
    private Double gpsAlt;

    /** 相对起飞点高度（米），来自气压计 */
    private Double relativeAltitude;

    // === 姿态 ===

    /** 无人机偏航角（度，0=正北） */
    private Double flightYaw;

    /** 无人机俯仰角（度） */
    private Double flightPitch;

    /** 无人机横滚角（度） */
    private Double flightRoll;

    /** 云台偏航角（度，绝对航向，0=正北，90=正东） */
    private Double gimbalYaw;

    /** 云台俯仰角（度，0=水平，负=向下） */
    private Double gimbalPitch;

    /** 云台横滚角（度） */
    private Double gimbalRoll;

    // === 图片尺寸 ===

    /** 图片宽度（像素） */
    private int imageWidth;

    /** 图片高度（像素） */
    private int imageHeight;

    /**
     * 返回相机制造商。
     *
     * @return 相机制造商
     */
    public String getMake() { return make; }
    /**
     * 设置{@code make}。
     *
     * @param make 相机制造商
     * @return 当前对象
     */
    public DjiPhotoMetadata setMake(String make) { this.make = make; return this; }
    /**
     * 返回相机型号。
     *
     * @return 相机型号
     */
    public String getModel() { return model; }
    /**
     * 设置{@code model}。
     *
     * @param model 相机型号
     * @return 当前对象
     */
    public DjiPhotoMetadata setModel(String model) { this.model = model; return this; }
    /**
     * 返回相机软件版本。
     *
     * @return 相机软件版本
     */
    public String getSoftware() { return software; }
    /**
     * 设置{@code software}。
     *
     * @param software 相机软件版本
     * @return 当前对象
     */
    public DjiPhotoMetadata setSoftware(String software) { this.software = software; return this; }
    /**
     * 返回照片修改时间。
     *
     * @return 照片修改时间
     */
    public String getModifyDate() { return modifyDate; }
    /**
     * 设置{@code modifyDate}。
     *
     * @param modifyDate 照片修改时间
     * @return 当前对象
     */
    public DjiPhotoMetadata setModifyDate(String modifyDate) { this.modifyDate = modifyDate; return this; }
    /**
     * 返回照片描述。
     *
     * @return 照片描述
     */
    public String getImageDescription() { return imageDescription; }
    /**
     * 设置{@code imageDescription}。
     *
     * @param value 照片描述
     * @return 当前对象
     */
    public DjiPhotoMetadata setImageDescription(String value) { this.imageDescription = value; return this; }
    /**
     * 返回曝光时间。
     *
     * @return 曝光时间
     */
    public Double getExposureTime() { return exposureTime; }
    /**
     * 设置{@code exposureTime}。
     *
     * @param value 曝光时间
     * @return 当前对象
     */
    public DjiPhotoMetadata setExposureTime(Double value) { this.exposureTime = value; return this; }
    /**
     * 返回光圈值。
     *
     * @return 光圈值
     */
    public Double getFNumber() { return fNumber; }
    /**
     * 设置{@code fNumber}。
     *
     * @param value 光圈值
     * @return 当前对象
     */
    public DjiPhotoMetadata setFNumber(Double value) { this.fNumber = value; return this; }
    /**
     * 返回ISO 感光度。
     *
     * @return ISO 感光度
     */
    public Integer getIso() { return iso; }
    /**
     * 设置{@code iso}。
     *
     * @param iso ISO 感光度
     * @return 当前对象
     */
    public DjiPhotoMetadata setIso(Integer iso) { this.iso = iso; return this; }
    /**
     * 返回照片原始拍摄时间。
     *
     * @return 照片原始拍摄时间
     */
    public String getDateTimeOriginal() { return dateTimeOriginal; }
    /**
     * 设置{@code dateTimeOriginal}。
     *
     * @param value 照片原始拍摄时间
     * @return 当前对象
     */
    public DjiPhotoMetadata setDateTimeOriginal(String value) { this.dateTimeOriginal = value; return this; }
    /**
     * 返回快门类型。
     *
     * @return 快门类型
     */
    public String getShutterType() { return shutterType; }
    /**
     * 设置{@code shutterType}。
     *
     * @param value 快门类型
     * @return 当前对象
     */
    public DjiPhotoMetadata setShutterType(String value) { this.shutterType = value; return this; }
    /**
     * 返回传感器帧率。
     *
     * @return 传感器帧率
     */
    public Double getSensorFPS() { return sensorFPS; }
    /**
     * 设置{@code sensorFPS}。
     *
     * @param value 传感器帧率
     * @return 当前对象
     */
    public DjiPhotoMetadata setSensorFPS(Double value) { this.sensorFPS = value; return this; }
    /**
     * 返回白平衡相关色温。
     *
     * @return 白平衡相关色温
     */
    public Integer getWhiteBalanceCCT() { return whiteBalanceCCT; }
    /**
     * 设置{@code whiteBalanceCCT}。
     *
     * @param value 白平衡相关色温
     * @return 当前对象
     */
    public DjiPhotoMetadata setWhiteBalanceCCT(Integer value) { this.whiteBalanceCCT = value; return this; }
    /**
     * 返回传感器温度。
     *
     * @return 传感器温度
     */
    public Double getSensorTemperature() { return sensorTemperature; }
    /**
     * 设置{@code sensorTemperature}。
     *
     * @param value 传感器温度
     * @return 当前对象
     */
    public DjiPhotoMetadata setSensorTemperature(Double value) { this.sensorTemperature = value; return this; }
    /**
     * 返回物理焦距。
     *
     * @return 物理焦距
     */
    public Double getFocalLength() { return focalLength; }
    /**
     * 设置{@code focalLength}。
     *
     * @param value 物理焦距
     * @return 当前对象
     */
    public DjiPhotoMetadata setFocalLength(Double value) { this.focalLength = value; return this; }
    /**
     * 返回焦平面水平方向分辨率。
     *
     * @return 焦平面水平方向分辨率
     */
    public Double getFocalPlaneXResolution() { return focalPlaneXResolution; }
    /**
     * 设置{@code focalPlaneXResolution}。
     *
     * @param value 焦平面水平方向分辨率
     * @return 当前对象
     */
    public DjiPhotoMetadata setFocalPlaneXResolution(Double value) { this.focalPlaneXResolution = value; return this; }
    /**
     * 返回焦平面垂直方向分辨率。
     *
     * @return 焦平面垂直方向分辨率
     */
    public Double getFocalPlaneYResolution() { return focalPlaneYResolution; }
    /**
     * 设置{@code focalPlaneYResolution}。
     *
     * @param value 焦平面垂直方向分辨率
     * @return 当前对象
     */
    public DjiPhotoMetadata setFocalPlaneYResolution(Double value) { this.focalPlaneYResolution = value; return this; }
    /**
     * 返回焦平面分辨率单位。
     *
     * @return 焦平面分辨率单位
     */
    public Integer getFocalPlaneResolutionUnit() { return focalPlaneResolutionUnit; }
    /**
     * 设置{@code focalPlaneResolutionUnit}。
     *
     * @param value 焦平面分辨率单位
     * @return 当前对象
     */
    public DjiPhotoMetadata setFocalPlaneResolutionUnit(Integer value) { this.focalPlaneResolutionUnit = value; return this; }
    /**
     * 返回35mm 等效焦距。
     *
     * @return 35mm 等效焦距
     */
    public Double getFocalLength35mm() { return focalLength35mm; }
    /**
     * 设置{@code focalLength35mm}。
     *
     * @param value 35mm 等效焦距
     * @return 当前对象
     */
    public DjiPhotoMetadata setFocalLength35mm(Double value) { this.focalLength35mm = value; return this; }
    /**
     * 返回仅从显式 DJI XMP 读取的标定焦距；MakerNote 不会为该字段赋值。
     *
     * @return 标定焦距（像素），图片未提供时为 {@code null}
     */
    public Double getCalibratedFocalLength() { return calibratedFocalLength; }

    /**
     * 设置显式 DJI XMP 中读取的标定焦距。
     *
     * @param value 标定焦距（像素）
     * @return 当前元数据对象
     */
    public DjiPhotoMetadata setCalibratedFocalLength(Double value) { this.calibratedFocalLength = value; return this; }
    /**
     * 返回相机机身序列号。
     *
     * @return 相机机身序列号
     */
    public String getBodySerialNumber() { return bodySerialNumber; }
    /**
     * 设置{@code bodySerialNumber}。
     *
     * @param value 相机机身序列号
     * @return 当前对象
     */
    public DjiPhotoMetadata setBodySerialNumber(String value) { this.bodySerialNumber = value; return this; }
    /**
     * 返回镜头规格。
     *
     * @return 镜头规格
     */
    public String getLensSpecification() { return lensSpecification; }
    /**
     * 设置{@code lensSpecification}。
     *
     * @param value 镜头规格
     * @return 当前对象
     */
    public DjiPhotoMetadata setLensSpecification(String value) { this.lensSpecification = value; return this; }
    /**
     * 返回相机唯一型号。
     *
     * @return 相机唯一型号
     */
    public String getUniqueCameraModel() { return uniqueCameraModel; }
    /**
     * 设置{@code uniqueCameraModel}。
     *
     * @param value 相机唯一型号
     * @return 当前对象
     */
    public DjiPhotoMetadata setUniqueCameraModel(String value) { this.uniqueCameraModel = value; return this; }
    /**
     * 返回产品名称。
     *
     * @return 产品名称
     */
    public String getProductName() { return productName; }
    /**
     * 设置{@code productName}。
     *
     * @param value 产品名称
     * @return 当前对象
     */
    public DjiPhotoMetadata setProductName(String value) { this.productName = value; return this; }
    /**
     * 返回DJI 飞行器型号。
     *
     * @return DJI 飞行器型号
     */
    public String getDroneModel() { return droneModel; }
    /**
     * 设置{@code droneModel}。
     *
     * @param value DJI 飞行器型号
     * @return 当前对象
     */
    public DjiPhotoMetadata setDroneModel(String value) { this.droneModel = value; return this; }
    /**
     * 返回飞行器序列号。
     *
     * @return 飞行器序列号
     */
    public String getDroneSerialNumber() { return droneSerialNumber; }
    /**
     * 设置{@code droneSerialNumber}。
     *
     * @param value 飞行器序列号
     * @return 当前对象
     */
    public DjiPhotoMetadata setDroneSerialNumber(String value) { this.droneSerialNumber = value; return this; }
    /**
     * 返回相机序列号。
     *
     * @return 相机序列号
     */
    public String getCameraSerialNumber() { return cameraSerialNumber; }
    /**
     * 设置{@code cameraSerialNumber}。
     *
     * @param value 相机序列号
     * @return 当前对象
     */
    public DjiPhotoMetadata setCameraSerialNumber(String value) { this.cameraSerialNumber = value; return this; }
    /**
     * 返回DJI 元数据版本。
     *
     * @return DJI 元数据版本
     */
    public String getDjiVersion() { return djiVersion; }
    /**
     * 设置{@code djiVersion}。
     *
     * @param value DJI 元数据版本
     * @return 当前对象
     */
    public DjiPhotoMetadata setDjiVersion(String value) { this.djiVersion = value; return this; }
    /**
     * 返回照片来源。
     *
     * @return 照片来源
     */
    public String getImageSource() { return imageSource; }
    /**
     * 设置{@code imageSource}。
     *
     * @param value 照片来源
     * @return 当前对象
     */
    public DjiPhotoMetadata setImageSource(String value) { this.imageSource = value; return this; }
    /**
     * 返回相机变焦倍率。
     *
     * @return 相机变焦倍率
     */
    public Double getZoomFactor() { return zoomFactor; }
    /**
     * 设置{@code zoomFactor}。
     *
     * @param value 相机变焦倍率
     * @return 当前对象
     */
    public DjiPhotoMetadata setZoomFactor(Double value) { this.zoomFactor = value; return this; }
    /**
     * 返回数字变焦倍率。
     *
     * @return 数字变焦倍率
     */
    public Double getDigitalZoomRatio() { return digitalZoomRatio; }
    /**
     * 设置{@code digitalZoomRatio}。
     *
     * @param value 数字变焦倍率
     * @return 当前对象
     */
    public DjiPhotoMetadata setDigitalZoomRatio(Double value) { this.digitalZoomRatio = value; return this; }
    /**
     * 返回DJI 飞行器类型代码。
     *
     * @return DJI 飞行器类型代码
     */
    public String getDroneTypeCode() { return droneTypeCode; }
    /**
     * 设置{@code droneTypeCode}。
     *
     * @param value DJI 飞行器类型代码
     * @return 当前对象
     */
    public DjiPhotoMetadata setDroneTypeCode(String value) { this.droneTypeCode = value; return this; }
    /**
     * 返回GPS 定位状态。
     *
     * @return GPS 定位状态
     */
    public String getGpsStatus() { return gpsStatus; }
    /**
     * 设置{@code gpsStatus}。
     *
     * @param value GPS 定位状态
     * @return 当前对象
     */
    public DjiPhotoMetadata setGpsStatus(String value) { this.gpsStatus = value; return this; }
    /**
     * 返回DJI 高度类型。
     *
     * @return DJI 高度类型
     */
    public String getAltitudeType() { return altitudeType; }
    /**
     * 设置{@code altitudeType}。
     *
     * @param value DJI 高度类型
     * @return 当前对象
     */
    public DjiPhotoMetadata setAltitudeType(String value) { this.altitudeType = value; return this; }
    /**
     * 返回RTK 定位状态标志。
     *
     * @return RTK 定位状态标志
     */
    public Integer getRtkFlag() { return rtkFlag; }
    /**
     * 设置{@code rtkFlag}。
     *
     * @param value RTK 定位状态标志
     * @return 当前对象
     */
    public DjiPhotoMetadata setRtkFlag(Integer value) { this.rtkFlag = value; return this; }
    /**
     * 返回RTK 经度标准差。
     *
     * @return RTK 经度标准差
     */
    public Double getRtkStdLon() { return rtkStdLon; }
    /**
     * 设置{@code rtkStdLon}。
     *
     * @param value RTK 经度标准差
     * @return 当前对象
     */
    public DjiPhotoMetadata setRtkStdLon(Double value) { this.rtkStdLon = value; return this; }
    /**
     * 返回RTK 纬度标准差。
     *
     * @return RTK 纬度标准差
     */
    public Double getRtkStdLat() { return rtkStdLat; }
    /**
     * 设置{@code rtkStdLat}。
     *
     * @param value RTK 纬度标准差
     * @return 当前对象
     */
    public DjiPhotoMetadata setRtkStdLat(Double value) { this.rtkStdLat = value; return this; }
    /**
     * 返回RTK 高程标准差。
     *
     * @return RTK 高程标准差
     */
    public Double getRtkStdHgt() { return rtkStdHgt; }
    /**
     * 设置{@code rtkStdHgt}。
     *
     * @param value RTK 高程标准差
     * @return 当前对象
     */
    public DjiPhotoMetadata setRtkStdHgt(Double value) { this.rtkStdHgt = value; return this; }
    /**
     * 返回RTK 差分数据龄期。
     *
     * @return RTK 差分数据龄期
     */
    public Double getRtkDiffAge() { return rtkDiffAge; }
    /**
     * 设置{@code rtkDiffAge}。
     *
     * @param value RTK 差分数据龄期
     * @return 当前对象
     */
    public DjiPhotoMetadata setRtkDiffAge(Double value) { this.rtkDiffAge = value; return this; }
    /**
     * 返回飞行器 X 轴速度。
     *
     * @return 飞行器 X 轴速度
     */
    public Double getFlightXSpeed() { return flightXSpeed; }
    /**
     * 设置{@code flightXSpeed}。
     *
     * @param value 飞行器 X 轴速度
     * @return 当前对象
     */
    public DjiPhotoMetadata setFlightXSpeed(Double value) { this.flightXSpeed = value; return this; }
    /**
     * 返回飞行器 Y 轴速度。
     *
     * @return 飞行器 Y 轴速度
     */
    public Double getFlightYSpeed() { return flightYSpeed; }
    /**
     * 设置{@code flightYSpeed}。
     *
     * @param value 飞行器 Y 轴速度
     * @return 当前对象
     */
    public DjiPhotoMetadata setFlightYSpeed(Double value) { this.flightYSpeed = value; return this; }
    /**
     * 返回飞行器 Z 轴速度。
     *
     * @return 飞行器 Z 轴速度
     */
    public Double getFlightZSpeed() { return flightZSpeed; }
    /**
     * 设置{@code flightZSpeed}。
     *
     * @param value 飞行器 Z 轴速度
     * @return 当前对象
     */
    public DjiPhotoMetadata setFlightZSpeed(Double value) { this.flightZSpeed = value; return this; }
    /**
     * 返回相机姿态是否反向。
     *
     * @return 相机姿态是否反向
     */
    public Integer getCamReverse() { return camReverse; }
    /**
     * 设置{@code camReverse}。
     *
     * @param value 相机姿态是否反向
     * @return 当前对象
     */
    public DjiPhotoMetadata setCamReverse(Integer value) { this.camReverse = value; return this; }
    /**
     * 返回云台姿态是否反向。
     *
     * @return 云台姿态是否反向
     */
    public Integer getGimbalReverse() { return gimbalReverse; }
    /**
     * 设置{@code gimbalReverse}。
     *
     * @param value 云台姿态是否反向
     * @return 当前对象
     */
    public DjiPhotoMetadata setGimbalReverse(Integer value) { this.gimbalReverse = value; return this; }
    /**
     * 返回DJI 测绘模式。
     *
     * @return DJI 测绘模式
     */
    public Integer getSurveyingMode() { return surveyingMode; }
    /**
     * 设置{@code surveyingMode}。
     *
     * @param value DJI 测绘模式
     * @return 当前对象
     */
    public DjiPhotoMetadata setSurveyingMode(Integer value) { this.surveyingMode = value; return this; }
    /**
     * 返回曝光时刻 UTC 时间。
     *
     * @return 曝光时刻 UTC 时间
     */
    public String getUtcAtExposure() { return utcAtExposure; }
    /**
     * 设置{@code utcAtExposure}。
     *
     * @param value 曝光时刻 UTC 时间
     * @return 当前对象
     */
    public DjiPhotoMetadata setUtcAtExposure(String value) { this.utcAtExposure = value; return this; }
    /**
     * 返回DJI 自定义元数据。
     *
     * @return DJI 自定义元数据
     */
    public String getSelfData() { return selfData; }
    /**
     * 设置{@code selfData}。
     *
     * @param value DJI 自定义元数据
     * @return 当前对象
     */
    public DjiPhotoMetadata setSelfData(String value) { this.selfData = value; return this; }
    /**
     * 返回GPS 纬度。
     *
     * @return GPS 纬度
     */
    public Double getGpsLat() { return gpsLat; }
    /**
     * 设置{@code gpsLat}。
     *
     * @param value GPS 纬度
     * @return 当前对象
     */
    public DjiPhotoMetadata setGpsLat(Double value) { this.gpsLat = value; return this; }
    /**
     * 返回GPS 经度。
     *
     * @return GPS 经度
     */
    public Double getGpsLon() { return gpsLon; }
    /**
     * 设置{@code gpsLon}。
     *
     * @param value GPS 经度
     * @return 当前对象
     */
    public DjiPhotoMetadata setGpsLon(Double value) { this.gpsLon = value; return this; }
    /**
     * 返回GPS 海拔高度。
     *
     * @return GPS 海拔高度
     */
    public Double getGpsAlt() { return gpsAlt; }
    /**
     * 设置{@code gpsAlt}。
     *
     * @param value GPS 海拔高度
     * @return 当前对象
     */
    public DjiPhotoMetadata setGpsAlt(Double value) { this.gpsAlt = value; return this; }
    /**
     * 返回相对起飞点高度。
     *
     * @return 相对起飞点高度
     */
    public Double getRelativeAltitude() { return relativeAltitude; }
    /**
     * 设置{@code relativeAltitude}。
     *
     * @param value 相对起飞点高度
     * @return 当前对象
     */
    public DjiPhotoMetadata setRelativeAltitude(Double value) { this.relativeAltitude = value; return this; }
    /**
     * 返回飞行器偏航角。
     *
     * @return 飞行器偏航角
     */
    public Double getFlightYaw() { return flightYaw; }
    /**
     * 设置{@code flightYaw}。
     *
     * @param value 飞行器偏航角
     * @return 当前对象
     */
    public DjiPhotoMetadata setFlightYaw(Double value) { this.flightYaw = value; return this; }
    /**
     * 返回飞行器俯仰角。
     *
     * @return 飞行器俯仰角
     */
    public Double getFlightPitch() { return flightPitch; }
    /**
     * 设置{@code flightPitch}。
     *
     * @param value 飞行器俯仰角
     * @return 当前对象
     */
    public DjiPhotoMetadata setFlightPitch(Double value) { this.flightPitch = value; return this; }
    /**
     * 返回飞行器横滚角。
     *
     * @return 飞行器横滚角
     */
    public Double getFlightRoll() { return flightRoll; }
    /**
     * 设置{@code flightRoll}。
     *
     * @param value 飞行器横滚角
     * @return 当前对象
     */
    public DjiPhotoMetadata setFlightRoll(Double value) { this.flightRoll = value; return this; }
    /**
     * 返回云台偏航角。
     *
     * @return 云台偏航角
     */
    public Double getGimbalYaw() { return gimbalYaw; }
    /**
     * 设置{@code gimbalYaw}。
     *
     * @param value 云台偏航角
     * @return 当前对象
     */
    public DjiPhotoMetadata setGimbalYaw(Double value) { this.gimbalYaw = value; return this; }
    /**
     * 返回云台俯仰角。
     *
     * @return 云台俯仰角
     */
    public Double getGimbalPitch() { return gimbalPitch; }
    /**
     * 设置{@code gimbalPitch}。
     *
     * @param value 云台俯仰角
     * @return 当前对象
     */
    public DjiPhotoMetadata setGimbalPitch(Double value) { this.gimbalPitch = value; return this; }
    /**
     * 返回云台横滚角。
     *
     * @return 云台横滚角
     */
    public Double getGimbalRoll() { return gimbalRoll; }
    /**
     * 设置{@code gimbalRoll}。
     *
     * @param value 云台横滚角
     * @return 当前对象
     */
    public DjiPhotoMetadata setGimbalRoll(Double value) { this.gimbalRoll = value; return this; }
    /**
     * 返回照片宽度像素数。
     *
     * @return 照片宽度像素数
     */
    public int getImageWidth() { return imageWidth; }
    /**
     * 设置{@code imageWidth}。
     *
     * @param value 照片宽度像素数
     * @return 当前对象
     */
    public DjiPhotoMetadata setImageWidth(int value) { this.imageWidth = value; return this; }
    /**
     * 返回照片高度像素数。
     *
     * @return 照片高度像素数
     */
    public int getImageHeight() { return imageHeight; }
    /**
     * 设置{@code imageHeight}。
     *
     * @param value 照片高度像素数
     * @return 当前对象
     */
    public DjiPhotoMetadata setImageHeight(int value) { this.imageHeight = value; return this; }
}
