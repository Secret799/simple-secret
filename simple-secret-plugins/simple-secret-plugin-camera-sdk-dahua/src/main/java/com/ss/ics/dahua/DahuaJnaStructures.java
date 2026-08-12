package com.ss.ics.dahua;

import com.sun.jna.IntegerType;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/** 本驱动使用的大华 NetSDK JNA 结构。 */
public final class DahuaJnaStructures {
    private DahuaJnaStructures() {
    }

    /** 大华 SDK 在受支持的 64 位平台使用的 64 位句柄。 */
    public static final class DahuaLong extends IntegerType {
        private static final long serialVersionUID = 1L;

        /**
         * 创建并初始化实例。
         */
        public DahuaLong() {
            this(0L);
        }

        /**
         * 创建并初始化实例。
         *
         * @param value SDK 句柄数值
         */
        public DahuaLong(long value) {
            super(8, value);
        }
    }

    /**
     * 大华 NetSDK 网络连接与超时参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "waitTime", "connectTime", "connectTryNum", "subConnectSpaceTime",
            "getDeviceInfoTime", "connectBufferSize", "getConnectionInfoTime",
            "searchRecordTime", "subDisconnectTime", "networkType", "playbackBufferSize",
            "detectDisconnectTime", "keepAliveInterval", "pictureBufferSize", "reserved"
    })
    public static final class NetworkParam extends Structure {
        /**
         * 登录响应等待时间，单位毫秒。
         */
        public int waitTime;
        /**
         * 连接超时时间，单位毫秒。
         */
        public int connectTime;
        /**
         * 连接重试次数。
         */
        public int connectTryNum;
        /**
         * 子连接建立间隔，单位毫秒。
         */
        public int subConnectSpaceTime;
        /**
         * 获取设备信息超时时间，单位毫秒。
         */
        public int getDeviceInfoTime;
        /**
         * 连接接收缓冲区大小，单位字节。
         */
        public int connectBufferSize;
        /**
         * 获取连接信息超时时间，单位毫秒。
         */
        public int getConnectionInfoTime;
        /**
         * 录像检索超时时间，单位毫秒。
         */
        public int searchRecordTime;
        /**
         * 子连接断开等待时间，单位毫秒。
         */
        public int subDisconnectTime;
        /**
         * 厂商 SDK 网络类型枚举值。
         */
        public byte networkType;
        /**
         * 回放缓冲区配置值。
         */
        public byte playbackBufferSize;
        /**
         * 断线检测间隔配置值。
         */
        public byte detectDisconnectTime;
        /**
         * 保活间隔配置值。
         */
        public byte keepAliveInterval;
        /**
         * 图片接收缓冲区大小，单位字节。
         */
        public int pictureBufferSize;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved = new byte[4];
    }

    /**
     * 大华 NetSDK 设备登录信息，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "serialNumber", "alarmInputCount", "alarmOutputCount", "diskCount", "deviceType",
            "channelCount", "limitLoginTime", "leftLoginTimes", "alignment",
            "lockLeftTime", "reserved"
    })
    public static final class DeviceInfoEx extends Structure {
        /**
         * 设备序列号字节缓冲区。
         */
        public byte[] serialNumber = new byte[48];
        /**
         * 报警输入通道数量。
         */
        public int alarmInputCount;
        /**
         * 报警输出通道数量。
         */
        public int alarmOutputCount;
        /**
         * 设备硬盘数量。
         */
        public int diskCount;
        /**
         * 厂商 SDK 设备类型枚举值。
         */
        public int deviceType;
        /**
         * 设备视频通道数量。
         */
        public int channelCount;
        /**
         * 登录失败次数限制。
         */
        public byte limitLoginTime;
        /**
         * 剩余允许登录次数。
         */
        public byte leftLoginTimes;
        /**
         * 原生结构内存对齐填充。
         */
        public byte[] alignment = new byte[2];
        /**
         * 账号剩余锁定时间，单位秒。
         */
        public int lockLeftTime;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved = new byte[24];
    }

    /**
     * 大华 NetSDK 高安全登录输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "size", "szIP", "port", "szUserName", "szPassword", "specialCapability",
            "alignment", "capabilityParameter", "tlsCapability"
    })
    public static final class HighSecurityLoginInput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 设备 IP 地址的零结尾字节缓冲区。
         */
        public byte[] szIP = new byte[64];
        /**
         * 监听或连接端口。
         */
        public int port;
        /**
         * 登录用户名的零结尾字节缓冲区。
         */
        public byte[] szUserName = new byte[64];
        /**
         * 登录密码的零结尾字节缓冲区。
         */
        public byte[] szPassword = new byte[64];
        /**
         * 厂商 SDK 特殊登录能力枚举值。
         */
        public int specialCapability;
        /**
         * 原生结构内存对齐填充。
         */
        public byte[] alignment = new byte[4];
        /**
         * 特殊登录能力附加参数指针。
         */
        public Pointer capabilityParameter;
        /**
         * TLS 登录能力标志。
         */
        public int tlsCapability;

        /**
         * 创建并初始化实例。
         */
        public HighSecurityLoginInput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 高安全登录输出参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "deviceInfo", "error", "reserved"})
    public static final class HighSecurityLoginOutput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 设备登录信息。
         */
        public DeviceInfoEx deviceInfo = new DeviceInfoEx();
        /**
         * 厂商 SDK 登录错误码。
         */
        public int error;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved = new byte[132];

        /**
         * 创建并初始化实例。
         */
        public HighSecurityLoginOutput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 实时数据回调时间戳，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "year", "month", "day", "hour", "minute", "second", "millisecond",
            "pts", "dts", "reserved"
    })
    public static final class DataCallbackTime extends Structure {
        /**
         * 年份。
         */
        public int year;
        /**
         * 月份。
         */
        public int month;
        /**
         * 日期。
         */
        public int day;
        /**
         * 小时。
         */
        public int hour;
        /**
         * 分钟。
         */
        public int minute;
        /**
         * 秒。
         */
        public int second;
        /**
         * 毫秒。
         */
        public int millisecond;
        /**
         * 显示时间戳。
         */
        public int pts;
        /**
         * 解码时间戳。
         */
        public int dts;
        /**
         * 厂商 SDK 保留字段。
         */
        public int[] reserved = new int[3];
    }

    /**
     * 大华 NetSDK 实时数据回调信息，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "size", "dataType", "buffer", "bufferSize", "time", "frameType", "frameSubType"
    })
    public static final class DataCallbackInfo extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 厂商 SDK 数据类型枚举值。
         */
        public int dataType;
        /**
         * 原生数据缓冲区指针。
         */
        public Pointer buffer;
        /**
         * 数据缓冲区长度，单位字节。
         */
        public int bufferSize;
        /**
         * 数据产生时间。
         */
        public DataCallbackTime time = new DataCallbackTime();
        /**
         * 厂商 SDK 帧类型枚举值。
         */
        public int frameType;
        /**
         * 厂商 SDK 帧子类型枚举值。
         */
        public int frameSubType;

        /**
         * 创建并初始化实例。
         */
        public DataCallbackInfo() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 实时预览输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "size", "channel", "window", "realPlayType", "realDataCallback", "dataType",
            "user", "saveFileName", "extendedRealDataCallback", "audioType",
            "dataCallback", "mp4Type"
    })
    public static final class RealPlayInput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 设备通道号。
         */
        public int channel;
        /**
         * 原生预览窗口句柄。
         */
        public Pointer window;
        /**
         * 厂商 SDK 实时预览类型枚举值。
         */
        public int realPlayType;
        /**
         * 实时码流回调函数。
         */
        public DahuaNativeLibrary.RealDataCallback realDataCallback;
        /**
         * 厂商 SDK 数据类型枚举值。
         */
        public int dataType;
        /**
         * 透传给回调函数的用户数据指针。
         */
        public Pointer user;
        /**
         * SDK 直存文件名的零结尾字节缓冲区。
         */
        public byte[] saveFileName = new byte[260];
        /**
         * 扩展实时码流回调函数。
         */
        public DahuaNativeLibrary.ExtendedRealDataCallback extendedRealDataCallback;
        /**
         * 厂商 SDK 音频类型枚举值。
         */
        public int audioType;
        /**
         * 结构化实时数据回调函数。
         */
        public DahuaNativeLibrary.DataCallback dataCallback;
        /**
         * 厂商 SDK MP4 封装类型枚举值。
         */
        public int mp4Type;

        /**
         * 创建并初始化实例。
         */
        public RealPlayInput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 实时预览输出参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size"})
    public static final class RealPlayOutput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;

        /**
         * 创建并初始化实例。
         */
        public RealPlayOutput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 设备本地时间，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"year", "month", "day", "hour", "minute", "second"})
    public static final class NetTime extends Structure {
        /**
         * 年份。
         */
        public int year;
        /**
         * 月份。
         */
        public int month;
        /**
         * 日期。
         */
        public int day;
        /**
         * 小时。
         */
        public int hour;
        /**
         * 分钟。
         */
        public int minute;
        /**
         * 秒。
         */
        public int second;
    }

    /**
     * 大华 NetSDK 归一化图像坐标点，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"x", "y"})
    public static final class Point extends Structure {
        /**
         * 横向归一化坐标。
         */
        public short x;
        /**
         * 纵向归一化坐标。
         */
        public short y;
    }

    /**
     * 大华 NetSDK 测温统计结果，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "meterType", "temperatureUnit", "average", "maximum", "minimum", "middle",
            "standardDeviation", "reserved"
    })
    public static final class TemperatureInfo extends Structure {
        /**
         * 厂商 SDK 测温类型枚举值。
         */
        public int meterType;
        /**
         * 厂商 SDK 温度单位枚举值。
         */
        public int temperatureUnit;
        /**
         * 平均温度。
         */
        public float average;
        /**
         * 最高温度。
         */
        public float maximum;
        /**
         * 最低温度。
         */
        public float minimum;
        /**
         * 中心点温度。
         */
        public float middle;
        /**
         * 温度标准差。
         */
        public float standardDeviation;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved = new byte[64];
    }

    /**
     * 大华 NetSDK 点测温输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "channel", "coordinate"})
    public static final class PointTemperatureInput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 设备通道号。
         */
        public int channel;
        /**
         * 测温坐标。
         */
        public Point coordinate = new Point();

        /**
         * 创建并初始化实例。
         */
        public PointTemperatureInput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 点测温输出参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "temperature"})
    public static final class PointTemperatureOutput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 测温结果。
         */
        public TemperatureInfo temperature = new TemperatureInfo();

        /**
         * 创建并初始化实例。
         */
        public PointTemperatureOutput() {
            size = size();
        }

        /**
         * 创建并初始化实例。
         *
         * @param pointer 原生结构内存地址
         */
        public PointTemperatureOutput(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    /**
     * 大华 NetSDK 测温规则检索条件，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "presetId", "ruleId", "meterType", "name", "channel", "reserved"
    })
    public static final class RadiometryCondition extends Structure {
        /**
         * 预置点编号。
         */
        public int presetId;
        /**
         * 测温规则编号。
         */
        public int ruleId;
        /**
         * 厂商 SDK 测温类型枚举值。
         */
        public int meterType;
        /**
         * 名称。
         */
        public byte[] name = new byte[64];
        /**
         * 设备通道号。
         */
        public int channel;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved = new byte[256];
    }

    /**
     * 大华 NetSDK 规则测温输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "condition"})
    public static final class ItemTemperatureInput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 测温规则检索条件。
         */
        public RadiometryCondition condition = new RadiometryCondition();

        /**
         * 创建并初始化实例。
         */
        public ItemTemperatureInput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 规则测温输出参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "temperature"})
    public static final class ItemTemperatureOutput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 测温结果。
         */
        public TemperatureInfo temperature = new TemperatureInfo();

        /**
         * 创建并初始化实例。
         */
        public ItemTemperatureOutput() {
            size = size();
        }

        /**
         * 创建并初始化实例。
         *
         * @param pointer 原生结构内存地址
         */
        public ItemTemperatureOutput(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    /**
     * 大华 NetSDK 区域测温统计结果，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "temperatureUnit", "average", "maximum", "minimum", "maximumPoint",
            "minimumPoint", "reserved"
    })
    public static final class RegionTemperatureInfo extends Structure {
        /**
         * 厂商 SDK 温度单位枚举值。
         */
        public int temperatureUnit;
        /**
         * 平均温度。
         */
        public int average;
        /**
         * 最高温度。
         */
        public int maximum;
        /**
         * 最低温度。
         */
        public int minimum;
        /**
         * 最高温度所在坐标。
         */
        public Point maximumPoint = new Point();
        /**
         * 最低温度所在坐标。
         */
        public Point minimumPoint = new Point();
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved = new byte[256];
    }

    /**
     * 大华 NetSDK 区域测温输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "channel", "pointCount", "polygon"})
    public static final class RegionTemperatureInput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 设备通道号。
         */
        public int channel;
        /**
         * 多边形有效坐标数量。
         */
        public int pointCount;
        /**
         * 区域多边形坐标数组，最多 8 个点。
         */
        public Point[] polygon = (Point[]) new Point().toArray(8);

        /**
         * 创建并初始化实例。
         */
        public RegionTemperatureInput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 区域测温输出参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "temperature"})
    public static final class RegionTemperatureOutput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 测温结果。
         */
        public RegionTemperatureInfo temperature = new RegionTemperatureInfo();

        /**
         * 创建并初始化实例。
         */
        public RegionTemperatureOutput() {
            size = size();
        }

        /**
         * 创建并初始化实例。
         *
         * @param pointer 原生结构内存地址
         */
        public RegionTemperatureOutput(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    /**
     * 大华 NetSDK 热成像数据订阅输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "channel", "callback", "user"})
    public static final class RadiometryAttachInput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 设备通道号。
         */
        public int channel;
        /**
         * 热成像数据回调函数。
         */
        public DahuaNativeLibrary.RadiometryCallback callback;
        /**
         * 透传给回调函数的用户数据指针。
         */
        public Pointer user;

        /**
         * 创建并初始化实例。
         */
        public RadiometryAttachInput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 热成像数据订阅输出参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size"})
    public static final class RadiometryAttachOutput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;

        /**
         * 创建并初始化实例。
         */
        public RadiometryAttachOutput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 热成像数据抓取输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "channel"})
    public static final class RadiometryFetchInput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 设备通道号。
         */
        public int channel;

        /**
         * 创建并初始化实例。
         */
        public RadiometryFetchInput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 热成像数据抓取输出参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "status"})
    public static final class RadiometryFetchOutput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 厂商 SDK 操作状态码。
         */
        public int status;

        /**
         * 创建并初始化实例。
         */
        public RadiometryFetchOutput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 热成像帧元数据，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "height", "width", "channel", "time", "length", "sensorType",
            "unzipR", "unzipB", "unzipF", "unzipO", "reserved"
    })
    public static final class ThermalMetadata extends Structure {
        /**
         * 高度。
         */
        public int height;
        /**
         * 宽度。
         */
        public int width;
        /**
         * 设备通道号。
         */
        public int channel;
        /**
         * 数据产生时间。
         */
        public NetTime time = new NetTime();
        /**
         * 热成像有效数据长度。
         */
        public int length;
        /**
         * 传感器类型字节缓冲区。
         */
        public byte[] sensorType = new byte[64];
        /**
         * 热成像解压参数 R。
         */
        public int unzipR;
        /**
         * 热成像解压参数 B。
         */
        public int unzipB;
        /**
         * 热成像解压参数 F。
         */
        public int unzipF;
        /**
         * 热成像解压参数 O。
         */
        public int unzipO;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved = new byte[256];
    }

    /**
     * 大华 NetSDK 热成像帧数据，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"metadata", "dataBuffer", "bufferSize", "reserved"})
    public static final class ThermalData extends Structure {
        /**
         * 热成像帧元数据。
         */
        public ThermalMetadata metadata = new ThermalMetadata();
        /**
         * 热成像原始数据缓冲区指针。
         */
        public Pointer dataBuffer;
        /**
         * 数据缓冲区长度，单位字节。
         */
        public int bufferSize;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved = new byte[512];
    }

    /**
     * 大华 NetSDK 历史测温记录，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "time", "presetId", "ruleId", "name", "coordinate", "channel",
            "temperature", "coordinates", "coordinateCount", "reserved"
    })
    public static final class RadiometryRecord extends Structure {
        /**
         * 数据产生时间。
         */
        public NetTime time = new NetTime();
        /**
         * 预置点编号。
         */
        public int presetId;
        /**
         * 测温规则编号。
         */
        public int ruleId;
        /**
         * 名称。
         */
        public byte[] name = new byte[64];
        /**
         * 测温坐标。
         */
        public Point coordinate = new Point();
        /**
         * 设备通道号。
         */
        public int channel;
        /**
         * 测温结果。
         */
        public TemperatureInfo temperature = new TemperatureInfo();
        /**
         * 测温区域坐标数组。
         */
        public Point[] coordinates = (Point[]) new Point().toArray(8);
        /**
         * 有效测温区域坐标数量。
         */
        public int coordinateCount;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved = new byte[220];
    }

    /**
     * 大华 NetSDK 历史测温检索输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "size", "startTime", "endTime", "meterType", "channel", "period"
    })
    public static final class RadiometrySearchInput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 检索开始时间。
         */
        public NetTime startTime = new NetTime();
        /**
         * 检索结束时间。
         */
        public NetTime endTime = new NetTime();
        /**
         * 厂商 SDK 测温类型枚举值。
         */
        public int meterType;
        /**
         * 设备通道号。
         */
        public int channel;
        /**
         * 聚合周期或检索步长。
         */
        public int period;

        /**
         * 创建并初始化实例。
         */
        public RadiometrySearchInput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 历史测温检索输出参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "finderHandle", "totalCount"})
    public static final class RadiometrySearchOutput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 厂商 SDK 检索句柄。
         */
        public int finderHandle;
        /**
         * 结果总数。
         */
        public int totalCount;

        /**
         * 创建并初始化实例。
         */
        public RadiometrySearchOutput() {
            size = size();
        }

        /**
         * 创建并初始化实例。
         *
         * @param pointer 原生结构内存地址
         */
        public RadiometrySearchOutput(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    /**
     * 大华 NetSDK 历史测温分页输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "finderHandle", "offset", "count"})
    public static final class RadiometryPageInput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 厂商 SDK 检索句柄。
         */
        public int finderHandle;
        /**
         * 偏移量。
         */
        public int offset;
        /**
         * 数量。
         */
        public int count;

        /**
         * 创建并初始化实例。
         */
        public RadiometryPageInput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 历史测温分页输出参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "found", "records"})
    public static final class RadiometryPageOutput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 本页实际返回记录数量。
         */
        public int found;
        /**
         * 测温记录数组，最多 32 条。
         */
        public RadiometryRecord[] records =
                (RadiometryRecord[]) new RadiometryRecord().toArray(32);

        /**
         * 创建并初始化实例。
         */
        public RadiometryPageOutput() {
            size = size();
        }

        /**
         * 创建并初始化实例。
         *
         * @param pointer 原生结构内存地址
         */
        public RadiometryPageOutput(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    /**
     * 大华 NetSDK 停止历史测温检索的输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size", "finderHandle"})
    public static final class RadiometryStopInput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;
        /**
         * 厂商 SDK 检索句柄。
         */
        public int finderHandle;

        /**
         * 创建并初始化实例。
         */
        public RadiometryStopInput() {
            size = size();
        }
    }

    /**
     * 大华 NetSDK 停止历史测温检索的输出参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"size"})
    public static final class RadiometryStopOutput extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int size;

        /**
         * 创建并初始化实例。
         */
        public RadiometryStopOutput() {
            size = size();
        }
    }
}
