package com.ss.ics.hikvision;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/** 本阶段原生调用所需的海康 JNA 结构。 */
public final class HikvisionJnaStructures {
    static final int DEVICE_ADDRESS_LENGTH = 129;
    static final int USERNAME_LENGTH = 64;
    static final int PASSWORD_LENGTH = 64;
    static final int SERIAL_NUMBER_LENGTH = 48;
    static final int STREAM_ID_LENGTH = 32;

    private HikvisionJnaStructures() {
    }

    /**
     * 海康 HCNetSDK 设备登录输入参数，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "sDeviceAddress", "byUseTransport", "wPort", "sUserName", "sPassword",
            "cbLoginResult", "pUser", "bUseAsynLogin", "byProxyType", "byUseUTCTime",
            "byLoginMode", "byHttps", "iProxyID", "byVerifyMode", "byRes3"
    })
    public static class UserLoginInfo extends Structure {
        /**
         * 设备地址的零结尾字节缓冲区。
         */
        public byte[] sDeviceAddress = new byte[DEVICE_ADDRESS_LENGTH];
        /**
         * 传输协议选择标志。
         */
        public byte byUseTransport;
        /**
         * 设备服务端口。
         */
        public short wPort;
        /**
         * 登录用户名的零结尾字节缓冲区。
         */
        public byte[] sUserName = new byte[USERNAME_LENGTH];
        /**
         * 登录密码的零结尾字节缓冲区。
         */
        public byte[] sPassword = new byte[PASSWORD_LENGTH];
        /**
         * 异步登录结果回调指针。
         */
        public Pointer cbLoginResult;
        /**
         * 回调用户数据指针。
         */
        public Pointer pUser;
        /**
         * 是否使用异步登录。
         */
        public boolean bUseAsynLogin;
        /**
         * 代理类型枚举值。
         */
        public byte byProxyType;
        /**
         * 是否使用 UTC 时间。
         */
        public byte byUseUTCTime;
        /**
         * 登录模式枚举值。
         */
        public byte byLoginMode;
        /**
         * 是否使用 HTTPS。
         */
        public byte byHttps;
        /**
         * 代理服务器编号。
         */
        public int iProxyID;
        /**
         * 证书校验模式枚举值。
         */
        public byte byVerifyMode;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] byRes3 = new byte[119];
    }

    /**
     * 海康 HCNetSDK 设备登录扩展信息，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "deviceInfoV30", "bySupportLock", "byRetryLoginTime", "byPasswordLevel",
            "byProxyType", "dwSurplusLockTime", "byCharEncodeType", "bySupportDev5",
            "byLoginMode", "byRes3", "iResidualValidity", "byResidualValidity",
            "bySingleStartDTalkChan", "bySingleDTalkChanNums", "byPassWordResetLevel",
            "bySupportStreamEncrypt", "byMarketType", "byRes2"
    })
    public static class DeviceInfoV40 extends Structure {
        /**
         * 设备基础能力信息。
         */
        public DeviceInfoV30 deviceInfoV30 = new DeviceInfoV30();
        /**
         * 是否支持账号锁定。
         */
        public byte bySupportLock;
        /**
         * 允许的登录重试次数。
         */
        public byte byRetryLoginTime;
        /**
         * 密码安全等级。
         */
        public byte byPasswordLevel;
        /**
         * 代理类型枚举值。
         */
        public byte byProxyType;
        /**
         * 账号剩余锁定时间，单位秒。
         */
        public int dwSurplusLockTime;
        /**
         * 设备字符编码类型。
         */
        public byte byCharEncodeType;
        /**
         * 设备扩展能力标志。
         */
        public byte bySupportDev5;
        /**
         * 登录模式枚举值。
         */
        public byte byLoginMode;
        /**
         * 厂商 SDK 保留字段。
         */
        public int byRes3;
        /**
         * 密码剩余有效期。
         */
        public int iResidualValidity;
        /**
         * 密码有效期状态。
         */
        public byte byResidualValidity;
        /**
         * 独立语音对讲起始通道。
         */
        public byte bySingleStartDTalkChan;
        /**
         * 独立语音对讲通道数量。
         */
        public byte bySingleDTalkChanNums;
        /**
         * 密码重置安全等级。
         */
        public byte byPassWordResetLevel;
        /**
         * 是否支持码流加密。
         */
        public byte bySupportStreamEncrypt;
        /**
         * 设备市场类型。
         */
        public byte byMarketType;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] byRes2 = new byte[238];
    }

    /**
     * 海康 HCNetSDK 设备基础能力信息，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "serialNumber", "alarmInputCount", "alarmOutputCount", "diskCount", "dvrType",
            "analogChannelCount", "startAnalogChannel", "audioChannelCount", "ipChannelCountLow",
            "zeroChannelCount", "mainProtocol", "subProtocol", "support", "support1", "support2",
            "deviceType", "support3", "multiStreamProtocol", "startDigitalChannel",
            "startDigitalTalkChannel", "ipChannelCountHigh", "support4", "languageType",
            "voiceInputChannelCount", "startVoiceInputChannel", "reserved3", "mirrorChannelCount",
            "startMirrorChannel", "reserved2"
    })
    public static class DeviceInfoV30 extends Structure {
        /**
         * 设备序列号字节缓冲区。
         */
        public byte[] serialNumber = new byte[SERIAL_NUMBER_LENGTH];
        /**
         * 报警输入通道数量。
         */
        public byte alarmInputCount;
        /**
         * 报警输出通道数量。
         */
        public byte alarmOutputCount;
        /**
         * 设备硬盘数量。
         */
        public byte diskCount;
        /**
         * DVR 设备类型枚举值。
         */
        public byte dvrType;
        /**
         * 模拟通道数量。
         */
        public byte analogChannelCount;
        /**
         * 模拟通道起始编号。
         */
        public byte startAnalogChannel;
        /**
         * 音频通道数量。
         */
        public byte audioChannelCount;
        /**
         * IP 通道数量低 8 位。
         */
        public byte ipChannelCountLow;
        /**
         * 零通道编码通道数量。
         */
        public byte zeroChannelCount;
        /**
         * 主码流协议能力位。
         */
        public byte mainProtocol;
        /**
         * 子码流协议能力位。
         */
        public byte subProtocol;
        /**
         * 设备能力位集合。
         */
        public byte support;
        /**
         * 设备扩展能力位集合 1。
         */
        public byte support1;
        /**
         * 设备扩展能力位集合 2。
         */
        public byte support2;
        /**
         * 厂商 SDK 设备类型枚举值。
         */
        public short deviceType;
        /**
         * 设备扩展能力位集合 3。
         */
        public byte support3;
        /**
         * 多码流协议能力位。
         */
        public byte multiStreamProtocol;
        /**
         * 数字通道起始编号。
         */
        public byte startDigitalChannel;
        /**
         * 数字语音对讲通道起始编号。
         */
        public byte startDigitalTalkChannel;
        /**
         * IP 通道数量高 8 位。
         */
        public byte ipChannelCountHigh;
        /**
         * 设备扩展能力位集合 4。
         */
        public byte support4;
        /**
         * 设备语言类型。
         */
        public byte languageType;
        /**
         * 语音输入通道数量。
         */
        public byte voiceInputChannelCount;
        /**
         * 语音输入通道起始编号。
         */
        public byte startVoiceInputChannel;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved3 = new byte[2];
        /**
         * 镜像通道数量。
         */
        public byte mirrorChannelCount;
        /**
         * 镜像通道起始编号。
         */
        public short startMirrorChannel;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] reserved2 = new byte[2];
    }

    /**
     * 海康 HCNetSDK 录像检索码流标识，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"dwSize", "byId", "dwChannel", "byRes"})
    public static class StreamInfo extends Structure {
        /**
         * 原生结构大小，单位字节。
         */
        public int dwSize;
        /**
         * 码流标识字节缓冲区。
         */
        public byte[] byId = new byte[STREAM_ID_LENGTH];
        /**
         * 设备通道号。
         */
        public int dwChannel;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] byRes = new byte[32];
    }

    /**
     * 海康 HCNetSDK 录像检索时间条件，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "wYear", "byMonth", "byDay", "byHour", "byMinute", "bySecond",
            "byLocalOrUTC", "wMillisecond", "cTimeDifferenceH", "cTimeDifferenceM"
    })
    public static class TimeSearchCondition extends Structure {
        /**
         * 年份。
         */
        public short wYear;
        /**
         * 月份。
         */
        public byte byMonth;
        /**
         * 日期。
         */
        public byte byDay;
        /**
         * 小时。
         */
        public byte byHour;
        /**
         * 分钟。
         */
        public byte byMinute;
        /**
         * 秒。
         */
        public byte bySecond;
        /**
         * 本地时间或 UTC 标志。
         */
        public byte byLocalOrUTC;
        /**
         * 毫秒。
         */
        public short wMillisecond;
        /**
         * 时区小时偏移。
         */
        public byte cTimeDifferenceH;
        /**
         * 时区分钟偏移。
         */
        public byte cTimeDifferenceM;
    }

    /**
     * 海康 HCNetSDK 录像文件检索条件，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "struStreamID", "struStartTime", "struStopTime", "byFindType",
            "byDrawFrame", "byQuickSearch", "byStreamType", "dwFileType",
            "dwVolumeNum", "byIsLocked", "byNeedCard", "byOnlyAudioFile",
            "bySpecialFindInfoType", "szCardNum", "szWorkingDeviceGUID",
            "dwTimeout", "byRes"
    })
    public static class FileSearchCondition extends Structure {
        /**
         * 待检索码流信息。
         */
        public StreamInfo struStreamID = new StreamInfo();
        /**
         * 检索开始时间。
         */
        public TimeSearchCondition struStartTime = new TimeSearchCondition();
        /**
         * 检索结束时间。
         */
        public TimeSearchCondition struStopTime = new TimeSearchCondition();
        /**
         * 录像检索类型枚举值。
         */
        public byte byFindType;
        /**
         * 是否抽帧检索。
         */
        public byte byDrawFrame;
        /**
         * 是否启用快速检索。
         */
        public byte byQuickSearch;
        /**
         * 码流类型枚举值。
         */
        public byte byStreamType;
        /**
         * 录像文件类型掩码。
         */
        public int dwFileType;
        /**
         * 存储卷编号。
         */
        public int dwVolumeNum;
        /**
         * 录像锁定状态筛选值。
         */
        public byte byIsLocked;
        /**
         * 是否按卡号筛选。
         */
        public byte byNeedCard;
        /**
         * 是否仅检索音频文件。
         */
        public byte byOnlyAudioFile;
        /**
         * 特殊检索信息类型。
         */
        public byte bySpecialFindInfoType;
        /**
         * 卡号字节缓冲区。
         */
        public byte[] szCardNum = new byte[32];
        /**
         * 工作设备 GUID 字节缓冲区。
         */
        public byte[] szWorkingDeviceGUID = new byte[16];
        /**
         * 检索超时时间，单位毫秒。
         */
        public int dwTimeout;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] byRes = new byte[252];
    }

    /**
     * 海康 HCNetSDK 录像文件时间，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "wYear", "byMonth", "byDay", "byHour", "byMinute", "bySecond",
            "cTimeDifferenceH", "cTimeDifferenceM", "byRes"
    })
    public static class TimeSearch extends Structure {
        /**
         * 年份。
         */
        public short wYear;
        /**
         * 月份。
         */
        public byte byMonth;
        /**
         * 日期。
         */
        public byte byDay;
        /**
         * 小时。
         */
        public byte byHour;
        /**
         * 分钟。
         */
        public byte byMinute;
        /**
         * 秒。
         */
        public byte bySecond;
        /**
         * 时区小时偏移。
         */
        public byte cTimeDifferenceH;
        /**
         * 时区分钟偏移。
         */
        public byte cTimeDifferenceM;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] byRes = new byte[3];
    }

    /**
     * 海康 HCNetSDK 设备 IPv4 地址，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"byIpAddress"})
    public static class IpAddress extends Structure {
        /**
         * IPv4 地址字节缓冲区。
         */
        public byte[] byIpAddress = new byte[16];
    }

    /**
     * 海康 HCNetSDK 设备网络地址，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({"struIP", "wPort", "byRes"})
    public static class Address extends Structure {
        /**
         * 设备 IP 地址。
         */
        public IpAddress struIP = new IpAddress();
        /**
         * 设备服务端口。
         */
        public short wPort;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] byRes = new byte[2];
    }

    /**
     * 海康 HCNetSDK 录像文件检索结果，字段顺序必须与厂商 C 结构保持一致。
     */
    @Structure.FieldOrder({
            "sFileName", "struStartTime", "struStopTime", "struAddr", "dwFileSize",
            "byLocked", "byFileType", "byQuickSearch", "byStreamType", "dwFileIndex",
            "sCardNum", "dwTotalLenH", "dwTotalLenL", "byBigFileType", "byRes"
    })
    public static class FileSearchData extends Structure {
        /**
         * 录像文件名字节缓冲区。
         */
        public byte[] sFileName = new byte[100];
        /**
         * 检索开始时间。
         */
        public TimeSearch struStartTime = new TimeSearch();
        /**
         * 检索结束时间。
         */
        public TimeSearch struStopTime = new TimeSearch();
        /**
         * 录像文件所在设备地址。
         */
        public Address struAddr = new Address();
        /**
         * 录像文件大小低 32 位。
         */
        public int dwFileSize;
        /**
         * 录像文件锁定状态。
         */
        public byte byLocked;
        /**
         * 录像文件类型枚举值。
         */
        public byte byFileType;
        /**
         * 是否启用快速检索。
         */
        public byte byQuickSearch;
        /**
         * 码流类型枚举值。
         */
        public byte byStreamType;
        /**
         * 录像文件索引。
         */
        public int dwFileIndex;
        /**
         * 关联卡号字节缓冲区。
         */
        public byte[] sCardNum = new byte[32];
        /**
         * 录像文件总长度高 32 位。
         */
        public int dwTotalLenH;
        /**
         * 录像文件总长度低 32 位。
         */
        public int dwTotalLenL;
        /**
         * 大文件类型枚举值。
         */
        public byte byBigFileType;
        /**
         * 厂商 SDK 保留字段。
         */
        public byte[] byRes = new byte[247];
    }
}
