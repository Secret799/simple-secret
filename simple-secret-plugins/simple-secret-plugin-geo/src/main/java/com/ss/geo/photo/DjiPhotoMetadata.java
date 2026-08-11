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

    public String getMake() { return make; }
    public DjiPhotoMetadata setMake(String make) { this.make = make; return this; }
    public String getModel() { return model; }
    public DjiPhotoMetadata setModel(String model) { this.model = model; return this; }
    public String getSoftware() { return software; }
    public DjiPhotoMetadata setSoftware(String software) { this.software = software; return this; }
    public String getModifyDate() { return modifyDate; }
    public DjiPhotoMetadata setModifyDate(String modifyDate) { this.modifyDate = modifyDate; return this; }
    public String getImageDescription() { return imageDescription; }
    public DjiPhotoMetadata setImageDescription(String value) { this.imageDescription = value; return this; }
    public Double getExposureTime() { return exposureTime; }
    public DjiPhotoMetadata setExposureTime(Double value) { this.exposureTime = value; return this; }
    public Double getFNumber() { return fNumber; }
    public DjiPhotoMetadata setFNumber(Double value) { this.fNumber = value; return this; }
    public Integer getIso() { return iso; }
    public DjiPhotoMetadata setIso(Integer iso) { this.iso = iso; return this; }
    public String getDateTimeOriginal() { return dateTimeOriginal; }
    public DjiPhotoMetadata setDateTimeOriginal(String value) { this.dateTimeOriginal = value; return this; }
    public String getShutterType() { return shutterType; }
    public DjiPhotoMetadata setShutterType(String value) { this.shutterType = value; return this; }
    public Double getSensorFPS() { return sensorFPS; }
    public DjiPhotoMetadata setSensorFPS(Double value) { this.sensorFPS = value; return this; }
    public Integer getWhiteBalanceCCT() { return whiteBalanceCCT; }
    public DjiPhotoMetadata setWhiteBalanceCCT(Integer value) { this.whiteBalanceCCT = value; return this; }
    public Double getSensorTemperature() { return sensorTemperature; }
    public DjiPhotoMetadata setSensorTemperature(Double value) { this.sensorTemperature = value; return this; }
    public Double getFocalLength() { return focalLength; }
    public DjiPhotoMetadata setFocalLength(Double value) { this.focalLength = value; return this; }
    public Double getFocalPlaneXResolution() { return focalPlaneXResolution; }
    public DjiPhotoMetadata setFocalPlaneXResolution(Double value) { this.focalPlaneXResolution = value; return this; }
    public Double getFocalPlaneYResolution() { return focalPlaneYResolution; }
    public DjiPhotoMetadata setFocalPlaneYResolution(Double value) { this.focalPlaneYResolution = value; return this; }
    public Integer getFocalPlaneResolutionUnit() { return focalPlaneResolutionUnit; }
    public DjiPhotoMetadata setFocalPlaneResolutionUnit(Integer value) { this.focalPlaneResolutionUnit = value; return this; }
    public Double getFocalLength35mm() { return focalLength35mm; }
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
    public String getBodySerialNumber() { return bodySerialNumber; }
    public DjiPhotoMetadata setBodySerialNumber(String value) { this.bodySerialNumber = value; return this; }
    public String getLensSpecification() { return lensSpecification; }
    public DjiPhotoMetadata setLensSpecification(String value) { this.lensSpecification = value; return this; }
    public String getUniqueCameraModel() { return uniqueCameraModel; }
    public DjiPhotoMetadata setUniqueCameraModel(String value) { this.uniqueCameraModel = value; return this; }
    public String getProductName() { return productName; }
    public DjiPhotoMetadata setProductName(String value) { this.productName = value; return this; }
    public String getDroneModel() { return droneModel; }
    public DjiPhotoMetadata setDroneModel(String value) { this.droneModel = value; return this; }
    public String getDroneSerialNumber() { return droneSerialNumber; }
    public DjiPhotoMetadata setDroneSerialNumber(String value) { this.droneSerialNumber = value; return this; }
    public String getCameraSerialNumber() { return cameraSerialNumber; }
    public DjiPhotoMetadata setCameraSerialNumber(String value) { this.cameraSerialNumber = value; return this; }
    public String getDjiVersion() { return djiVersion; }
    public DjiPhotoMetadata setDjiVersion(String value) { this.djiVersion = value; return this; }
    public String getImageSource() { return imageSource; }
    public DjiPhotoMetadata setImageSource(String value) { this.imageSource = value; return this; }
    public Double getZoomFactor() { return zoomFactor; }
    public DjiPhotoMetadata setZoomFactor(Double value) { this.zoomFactor = value; return this; }
    public Double getDigitalZoomRatio() { return digitalZoomRatio; }
    public DjiPhotoMetadata setDigitalZoomRatio(Double value) { this.digitalZoomRatio = value; return this; }
    public String getDroneTypeCode() { return droneTypeCode; }
    public DjiPhotoMetadata setDroneTypeCode(String value) { this.droneTypeCode = value; return this; }
    public String getGpsStatus() { return gpsStatus; }
    public DjiPhotoMetadata setGpsStatus(String value) { this.gpsStatus = value; return this; }
    public String getAltitudeType() { return altitudeType; }
    public DjiPhotoMetadata setAltitudeType(String value) { this.altitudeType = value; return this; }
    public Integer getRtkFlag() { return rtkFlag; }
    public DjiPhotoMetadata setRtkFlag(Integer value) { this.rtkFlag = value; return this; }
    public Double getRtkStdLon() { return rtkStdLon; }
    public DjiPhotoMetadata setRtkStdLon(Double value) { this.rtkStdLon = value; return this; }
    public Double getRtkStdLat() { return rtkStdLat; }
    public DjiPhotoMetadata setRtkStdLat(Double value) { this.rtkStdLat = value; return this; }
    public Double getRtkStdHgt() { return rtkStdHgt; }
    public DjiPhotoMetadata setRtkStdHgt(Double value) { this.rtkStdHgt = value; return this; }
    public Double getRtkDiffAge() { return rtkDiffAge; }
    public DjiPhotoMetadata setRtkDiffAge(Double value) { this.rtkDiffAge = value; return this; }
    public Double getFlightXSpeed() { return flightXSpeed; }
    public DjiPhotoMetadata setFlightXSpeed(Double value) { this.flightXSpeed = value; return this; }
    public Double getFlightYSpeed() { return flightYSpeed; }
    public DjiPhotoMetadata setFlightYSpeed(Double value) { this.flightYSpeed = value; return this; }
    public Double getFlightZSpeed() { return flightZSpeed; }
    public DjiPhotoMetadata setFlightZSpeed(Double value) { this.flightZSpeed = value; return this; }
    public Integer getCamReverse() { return camReverse; }
    public DjiPhotoMetadata setCamReverse(Integer value) { this.camReverse = value; return this; }
    public Integer getGimbalReverse() { return gimbalReverse; }
    public DjiPhotoMetadata setGimbalReverse(Integer value) { this.gimbalReverse = value; return this; }
    public Integer getSurveyingMode() { return surveyingMode; }
    public DjiPhotoMetadata setSurveyingMode(Integer value) { this.surveyingMode = value; return this; }
    public String getUtcAtExposure() { return utcAtExposure; }
    public DjiPhotoMetadata setUtcAtExposure(String value) { this.utcAtExposure = value; return this; }
    public String getSelfData() { return selfData; }
    public DjiPhotoMetadata setSelfData(String value) { this.selfData = value; return this; }
    public Double getGpsLat() { return gpsLat; }
    public DjiPhotoMetadata setGpsLat(Double value) { this.gpsLat = value; return this; }
    public Double getGpsLon() { return gpsLon; }
    public DjiPhotoMetadata setGpsLon(Double value) { this.gpsLon = value; return this; }
    public Double getGpsAlt() { return gpsAlt; }
    public DjiPhotoMetadata setGpsAlt(Double value) { this.gpsAlt = value; return this; }
    public Double getRelativeAltitude() { return relativeAltitude; }
    public DjiPhotoMetadata setRelativeAltitude(Double value) { this.relativeAltitude = value; return this; }
    public Double getFlightYaw() { return flightYaw; }
    public DjiPhotoMetadata setFlightYaw(Double value) { this.flightYaw = value; return this; }
    public Double getFlightPitch() { return flightPitch; }
    public DjiPhotoMetadata setFlightPitch(Double value) { this.flightPitch = value; return this; }
    public Double getFlightRoll() { return flightRoll; }
    public DjiPhotoMetadata setFlightRoll(Double value) { this.flightRoll = value; return this; }
    public Double getGimbalYaw() { return gimbalYaw; }
    public DjiPhotoMetadata setGimbalYaw(Double value) { this.gimbalYaw = value; return this; }
    public Double getGimbalPitch() { return gimbalPitch; }
    public DjiPhotoMetadata setGimbalPitch(Double value) { this.gimbalPitch = value; return this; }
    public Double getGimbalRoll() { return gimbalRoll; }
    public DjiPhotoMetadata setGimbalRoll(Double value) { this.gimbalRoll = value; return this; }
    public int getImageWidth() { return imageWidth; }
    public DjiPhotoMetadata setImageWidth(int value) { this.imageWidth = value; return this; }
    public int getImageHeight() { return imageHeight; }
    public DjiPhotoMetadata setImageHeight(int value) { this.imageHeight = value; return this; }
}
