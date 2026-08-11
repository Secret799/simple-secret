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

    @Structure.FieldOrder({
            "sDeviceAddress", "byUseTransport", "wPort", "sUserName", "sPassword",
            "cbLoginResult", "pUser", "bUseAsynLogin", "byProxyType", "byUseUTCTime",
            "byLoginMode", "byHttps", "iProxyID", "byVerifyMode", "byRes3"
    })
    public static class UserLoginInfo extends Structure {
        public byte[] sDeviceAddress = new byte[DEVICE_ADDRESS_LENGTH];
        public byte byUseTransport;
        public short wPort;
        public byte[] sUserName = new byte[USERNAME_LENGTH];
        public byte[] sPassword = new byte[PASSWORD_LENGTH];
        public Pointer cbLoginResult;
        public Pointer pUser;
        public boolean bUseAsynLogin;
        public byte byProxyType;
        public byte byUseUTCTime;
        public byte byLoginMode;
        public byte byHttps;
        public int iProxyID;
        public byte byVerifyMode;
        public byte[] byRes3 = new byte[119];
    }

    @Structure.FieldOrder({
            "deviceInfoV30", "bySupportLock", "byRetryLoginTime", "byPasswordLevel",
            "byProxyType", "dwSurplusLockTime", "byCharEncodeType", "bySupportDev5",
            "byLoginMode", "byRes3", "iResidualValidity", "byResidualValidity",
            "bySingleStartDTalkChan", "bySingleDTalkChanNums", "byPassWordResetLevel",
            "bySupportStreamEncrypt", "byMarketType", "byRes2"
    })
    public static class DeviceInfoV40 extends Structure {
        public DeviceInfoV30 deviceInfoV30 = new DeviceInfoV30();
        public byte bySupportLock;
        public byte byRetryLoginTime;
        public byte byPasswordLevel;
        public byte byProxyType;
        public int dwSurplusLockTime;
        public byte byCharEncodeType;
        public byte bySupportDev5;
        public byte byLoginMode;
        public int byRes3;
        public int iResidualValidity;
        public byte byResidualValidity;
        public byte bySingleStartDTalkChan;
        public byte bySingleDTalkChanNums;
        public byte byPassWordResetLevel;
        public byte bySupportStreamEncrypt;
        public byte byMarketType;
        public byte[] byRes2 = new byte[238];
    }

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
        public byte[] serialNumber = new byte[SERIAL_NUMBER_LENGTH];
        public byte alarmInputCount;
        public byte alarmOutputCount;
        public byte diskCount;
        public byte dvrType;
        public byte analogChannelCount;
        public byte startAnalogChannel;
        public byte audioChannelCount;
        public byte ipChannelCountLow;
        public byte zeroChannelCount;
        public byte mainProtocol;
        public byte subProtocol;
        public byte support;
        public byte support1;
        public byte support2;
        public short deviceType;
        public byte support3;
        public byte multiStreamProtocol;
        public byte startDigitalChannel;
        public byte startDigitalTalkChannel;
        public byte ipChannelCountHigh;
        public byte support4;
        public byte languageType;
        public byte voiceInputChannelCount;
        public byte startVoiceInputChannel;
        public byte[] reserved3 = new byte[2];
        public byte mirrorChannelCount;
        public short startMirrorChannel;
        public byte[] reserved2 = new byte[2];
    }

    @Structure.FieldOrder({"dwSize", "byId", "dwChannel", "byRes"})
    public static class StreamInfo extends Structure {
        public int dwSize;
        public byte[] byId = new byte[STREAM_ID_LENGTH];
        public int dwChannel;
        public byte[] byRes = new byte[32];
    }

    @Structure.FieldOrder({
            "wYear", "byMonth", "byDay", "byHour", "byMinute", "bySecond",
            "byLocalOrUTC", "wMillisecond", "cTimeDifferenceH", "cTimeDifferenceM"
    })
    public static class TimeSearchCondition extends Structure {
        public short wYear;
        public byte byMonth;
        public byte byDay;
        public byte byHour;
        public byte byMinute;
        public byte bySecond;
        public byte byLocalOrUTC;
        public short wMillisecond;
        public byte cTimeDifferenceH;
        public byte cTimeDifferenceM;
    }

    @Structure.FieldOrder({
            "struStreamID", "struStartTime", "struStopTime", "byFindType",
            "byDrawFrame", "byQuickSearch", "byStreamType", "dwFileType",
            "dwVolumeNum", "byIsLocked", "byNeedCard", "byOnlyAudioFile",
            "bySpecialFindInfoType", "szCardNum", "szWorkingDeviceGUID",
            "dwTimeout", "byRes"
    })
    public static class FileSearchCondition extends Structure {
        public StreamInfo struStreamID = new StreamInfo();
        public TimeSearchCondition struStartTime = new TimeSearchCondition();
        public TimeSearchCondition struStopTime = new TimeSearchCondition();
        public byte byFindType;
        public byte byDrawFrame;
        public byte byQuickSearch;
        public byte byStreamType;
        public int dwFileType;
        public int dwVolumeNum;
        public byte byIsLocked;
        public byte byNeedCard;
        public byte byOnlyAudioFile;
        public byte bySpecialFindInfoType;
        public byte[] szCardNum = new byte[32];
        public byte[] szWorkingDeviceGUID = new byte[16];
        public int dwTimeout;
        public byte[] byRes = new byte[252];
    }

    @Structure.FieldOrder({
            "wYear", "byMonth", "byDay", "byHour", "byMinute", "bySecond",
            "cTimeDifferenceH", "cTimeDifferenceM", "byRes"
    })
    public static class TimeSearch extends Structure {
        public short wYear;
        public byte byMonth;
        public byte byDay;
        public byte byHour;
        public byte byMinute;
        public byte bySecond;
        public byte cTimeDifferenceH;
        public byte cTimeDifferenceM;
        public byte[] byRes = new byte[3];
    }

    @Structure.FieldOrder({"byIpAddress"})
    public static class IpAddress extends Structure {
        public byte[] byIpAddress = new byte[16];
    }

    @Structure.FieldOrder({"struIP", "wPort", "byRes"})
    public static class Address extends Structure {
        public IpAddress struIP = new IpAddress();
        public short wPort;
        public byte[] byRes = new byte[2];
    }

    @Structure.FieldOrder({
            "sFileName", "struStartTime", "struStopTime", "struAddr", "dwFileSize",
            "byLocked", "byFileType", "byQuickSearch", "byStreamType", "dwFileIndex",
            "sCardNum", "dwTotalLenH", "dwTotalLenL", "byBigFileType", "byRes"
    })
    public static class FileSearchData extends Structure {
        public byte[] sFileName = new byte[100];
        public TimeSearch struStartTime = new TimeSearch();
        public TimeSearch struStopTime = new TimeSearch();
        public Address struAddr = new Address();
        public int dwFileSize;
        public byte byLocked;
        public byte byFileType;
        public byte byQuickSearch;
        public byte byStreamType;
        public int dwFileIndex;
        public byte[] sCardNum = new byte[32];
        public int dwTotalLenH;
        public int dwTotalLenL;
        public byte byBigFileType;
        public byte[] byRes = new byte[247];
    }
}
