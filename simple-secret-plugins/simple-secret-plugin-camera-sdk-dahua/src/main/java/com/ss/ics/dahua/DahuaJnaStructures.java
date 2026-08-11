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

        public DahuaLong() {
            this(0L);
        }

        public DahuaLong(long value) {
            super(8, value);
        }
    }

    @Structure.FieldOrder({
            "waitTime", "connectTime", "connectTryNum", "subConnectSpaceTime",
            "getDeviceInfoTime", "connectBufferSize", "getConnectionInfoTime",
            "searchRecordTime", "subDisconnectTime", "networkType", "playbackBufferSize",
            "detectDisconnectTime", "keepAliveInterval", "pictureBufferSize", "reserved"
    })
    public static final class NetworkParam extends Structure {
        public int waitTime;
        public int connectTime;
        public int connectTryNum;
        public int subConnectSpaceTime;
        public int getDeviceInfoTime;
        public int connectBufferSize;
        public int getConnectionInfoTime;
        public int searchRecordTime;
        public int subDisconnectTime;
        public byte networkType;
        public byte playbackBufferSize;
        public byte detectDisconnectTime;
        public byte keepAliveInterval;
        public int pictureBufferSize;
        public byte[] reserved = new byte[4];
    }

    @Structure.FieldOrder({
            "serialNumber", "alarmInputCount", "alarmOutputCount", "diskCount", "deviceType",
            "channelCount", "limitLoginTime", "leftLoginTimes", "alignment",
            "lockLeftTime", "reserved"
    })
    public static final class DeviceInfoEx extends Structure {
        public byte[] serialNumber = new byte[48];
        public int alarmInputCount;
        public int alarmOutputCount;
        public int diskCount;
        public int deviceType;
        public int channelCount;
        public byte limitLoginTime;
        public byte leftLoginTimes;
        public byte[] alignment = new byte[2];
        public int lockLeftTime;
        public byte[] reserved = new byte[24];
    }

    @Structure.FieldOrder({
            "size", "szIP", "port", "szUserName", "szPassword", "specialCapability",
            "alignment", "capabilityParameter", "tlsCapability"
    })
    public static final class HighSecurityLoginInput extends Structure {
        public int size;
        public byte[] szIP = new byte[64];
        public int port;
        public byte[] szUserName = new byte[64];
        public byte[] szPassword = new byte[64];
        public int specialCapability;
        public byte[] alignment = new byte[4];
        public Pointer capabilityParameter;
        public int tlsCapability;

        public HighSecurityLoginInput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size", "deviceInfo", "error", "reserved"})
    public static final class HighSecurityLoginOutput extends Structure {
        public int size;
        public DeviceInfoEx deviceInfo = new DeviceInfoEx();
        public int error;
        public byte[] reserved = new byte[132];

        public HighSecurityLoginOutput() {
            size = size();
        }
    }

    @Structure.FieldOrder({
            "year", "month", "day", "hour", "minute", "second", "millisecond",
            "pts", "dts", "reserved"
    })
    public static final class DataCallbackTime extends Structure {
        public int year;
        public int month;
        public int day;
        public int hour;
        public int minute;
        public int second;
        public int millisecond;
        public int pts;
        public int dts;
        public int[] reserved = new int[3];
    }

    @Structure.FieldOrder({
            "size", "dataType", "buffer", "bufferSize", "time", "frameType", "frameSubType"
    })
    public static final class DataCallbackInfo extends Structure {
        public int size;
        public int dataType;
        public Pointer buffer;
        public int bufferSize;
        public DataCallbackTime time = new DataCallbackTime();
        public int frameType;
        public int frameSubType;

        public DataCallbackInfo() {
            size = size();
        }
    }

    @Structure.FieldOrder({
            "size", "channel", "window", "realPlayType", "realDataCallback", "dataType",
            "user", "saveFileName", "extendedRealDataCallback", "audioType",
            "dataCallback", "mp4Type"
    })
    public static final class RealPlayInput extends Structure {
        public int size;
        public int channel;
        public Pointer window;
        public int realPlayType;
        public DahuaNativeLibrary.RealDataCallback realDataCallback;
        public int dataType;
        public Pointer user;
        public byte[] saveFileName = new byte[260];
        public DahuaNativeLibrary.ExtendedRealDataCallback extendedRealDataCallback;
        public int audioType;
        public DahuaNativeLibrary.DataCallback dataCallback;
        public int mp4Type;

        public RealPlayInput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size"})
    public static final class RealPlayOutput extends Structure {
        public int size;

        public RealPlayOutput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"year", "month", "day", "hour", "minute", "second"})
    public static final class NetTime extends Structure {
        public int year;
        public int month;
        public int day;
        public int hour;
        public int minute;
        public int second;
    }

    @Structure.FieldOrder({"x", "y"})
    public static final class Point extends Structure {
        public short x;
        public short y;
    }

    @Structure.FieldOrder({
            "meterType", "temperatureUnit", "average", "maximum", "minimum", "middle",
            "standardDeviation", "reserved"
    })
    public static final class TemperatureInfo extends Structure {
        public int meterType;
        public int temperatureUnit;
        public float average;
        public float maximum;
        public float minimum;
        public float middle;
        public float standardDeviation;
        public byte[] reserved = new byte[64];
    }

    @Structure.FieldOrder({"size", "channel", "coordinate"})
    public static final class PointTemperatureInput extends Structure {
        public int size;
        public int channel;
        public Point coordinate = new Point();

        public PointTemperatureInput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size", "temperature"})
    public static final class PointTemperatureOutput extends Structure {
        public int size;
        public TemperatureInfo temperature = new TemperatureInfo();

        public PointTemperatureOutput() {
            size = size();
        }

        public PointTemperatureOutput(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({
            "presetId", "ruleId", "meterType", "name", "channel", "reserved"
    })
    public static final class RadiometryCondition extends Structure {
        public int presetId;
        public int ruleId;
        public int meterType;
        public byte[] name = new byte[64];
        public int channel;
        public byte[] reserved = new byte[256];
    }

    @Structure.FieldOrder({"size", "condition"})
    public static final class ItemTemperatureInput extends Structure {
        public int size;
        public RadiometryCondition condition = new RadiometryCondition();

        public ItemTemperatureInput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size", "temperature"})
    public static final class ItemTemperatureOutput extends Structure {
        public int size;
        public TemperatureInfo temperature = new TemperatureInfo();

        public ItemTemperatureOutput() {
            size = size();
        }

        public ItemTemperatureOutput(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({
            "temperatureUnit", "average", "maximum", "minimum", "maximumPoint",
            "minimumPoint", "reserved"
    })
    public static final class RegionTemperatureInfo extends Structure {
        public int temperatureUnit;
        public int average;
        public int maximum;
        public int minimum;
        public Point maximumPoint = new Point();
        public Point minimumPoint = new Point();
        public byte[] reserved = new byte[256];
    }

    @Structure.FieldOrder({"size", "channel", "pointCount", "polygon"})
    public static final class RegionTemperatureInput extends Structure {
        public int size;
        public int channel;
        public int pointCount;
        public Point[] polygon = (Point[]) new Point().toArray(8);

        public RegionTemperatureInput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size", "temperature"})
    public static final class RegionTemperatureOutput extends Structure {
        public int size;
        public RegionTemperatureInfo temperature = new RegionTemperatureInfo();

        public RegionTemperatureOutput() {
            size = size();
        }

        public RegionTemperatureOutput(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({"size", "channel", "callback", "user"})
    public static final class RadiometryAttachInput extends Structure {
        public int size;
        public int channel;
        public DahuaNativeLibrary.RadiometryCallback callback;
        public Pointer user;

        public RadiometryAttachInput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size"})
    public static final class RadiometryAttachOutput extends Structure {
        public int size;

        public RadiometryAttachOutput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size", "channel"})
    public static final class RadiometryFetchInput extends Structure {
        public int size;
        public int channel;

        public RadiometryFetchInput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size", "status"})
    public static final class RadiometryFetchOutput extends Structure {
        public int size;
        public int status;

        public RadiometryFetchOutput() {
            size = size();
        }
    }

    @Structure.FieldOrder({
            "height", "width", "channel", "time", "length", "sensorType",
            "unzipR", "unzipB", "unzipF", "unzipO", "reserved"
    })
    public static final class ThermalMetadata extends Structure {
        public int height;
        public int width;
        public int channel;
        public NetTime time = new NetTime();
        public int length;
        public byte[] sensorType = new byte[64];
        public int unzipR;
        public int unzipB;
        public int unzipF;
        public int unzipO;
        public byte[] reserved = new byte[256];
    }

    @Structure.FieldOrder({"metadata", "dataBuffer", "bufferSize", "reserved"})
    public static final class ThermalData extends Structure {
        public ThermalMetadata metadata = new ThermalMetadata();
        public Pointer dataBuffer;
        public int bufferSize;
        public byte[] reserved = new byte[512];
    }

    @Structure.FieldOrder({
            "time", "presetId", "ruleId", "name", "coordinate", "channel",
            "temperature", "coordinates", "coordinateCount", "reserved"
    })
    public static final class RadiometryRecord extends Structure {
        public NetTime time = new NetTime();
        public int presetId;
        public int ruleId;
        public byte[] name = new byte[64];
        public Point coordinate = new Point();
        public int channel;
        public TemperatureInfo temperature = new TemperatureInfo();
        public Point[] coordinates = (Point[]) new Point().toArray(8);
        public int coordinateCount;
        public byte[] reserved = new byte[220];
    }

    @Structure.FieldOrder({
            "size", "startTime", "endTime", "meterType", "channel", "period"
    })
    public static final class RadiometrySearchInput extends Structure {
        public int size;
        public NetTime startTime = new NetTime();
        public NetTime endTime = new NetTime();
        public int meterType;
        public int channel;
        public int period;

        public RadiometrySearchInput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size", "finderHandle", "totalCount"})
    public static final class RadiometrySearchOutput extends Structure {
        public int size;
        public int finderHandle;
        public int totalCount;

        public RadiometrySearchOutput() {
            size = size();
        }

        public RadiometrySearchOutput(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({"size", "finderHandle", "offset", "count"})
    public static final class RadiometryPageInput extends Structure {
        public int size;
        public int finderHandle;
        public int offset;
        public int count;

        public RadiometryPageInput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size", "found", "records"})
    public static final class RadiometryPageOutput extends Structure {
        public int size;
        public int found;
        public RadiometryRecord[] records =
                (RadiometryRecord[]) new RadiometryRecord().toArray(32);

        public RadiometryPageOutput() {
            size = size();
        }

        public RadiometryPageOutput(Pointer pointer) {
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({"size", "finderHandle"})
    public static final class RadiometryStopInput extends Structure {
        public int size;
        public int finderHandle;

        public RadiometryStopInput() {
            size = size();
        }
    }

    @Structure.FieldOrder({"size"})
    public static final class RadiometryStopOutput extends Structure {
        public int size;

        public RadiometryStopOutput() {
            size = size();
        }
    }
}
