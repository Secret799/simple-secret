package com.ss.gb28181.internal;

import com.ss.gb28181.CatalogItem;
import com.ss.gb28181.DeviceInfo;
import com.ss.gb28181.DeviceStatus;
import com.ss.gb28181.GbAlarm;
import com.ss.gb28181.GbAlarmResetCommand;
import com.ss.gb28181.GbAlarmSubscriptionRequest;
import com.ss.gb28181.GbCatalogNotification;
import com.ss.gb28181.GbCatalogSubscriptionRequest;
import com.ss.gb28181.GbMobilePosition;
import com.ss.gb28181.GbMobilePositionNotification;
import com.ss.gb28181.GbMobilePositionSubscriptionRequest;
import com.ss.gb28181.GbGuardCommand;
import com.ss.gb28181.GbDragZoomCommand;
import com.ss.gb28181.GbRecordControlCommand;
import com.ss.gb28181.HomePosition;
import com.ss.gb28181.PtzPosition;
import com.ss.gb28181.GbTeleBootCommand;
import com.ss.gb28181.PresetItem;
import com.ss.gb28181.RecordItem;
import com.ss.gb28181.RecordQuery;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Internal codec for the supported GB28181 MANSCDP messages. */
public final class GbXml {

    private static final Pattern DEVICE_ID = Pattern.compile("[0-9]{20}");
    private static final Pattern ALARM_DEVICE_ID = Pattern.compile("(?:[0-9]{10}|[0-9]{20})");
    private static final Pattern XML_DOUBLE = Pattern.compile(
            "[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?)|(?:\\.[0-9]+))(?:[eE][+-]?[0-9]+)?");
    private static final Pattern CATALOG_ID = Pattern.compile("[0-9]{2,20}");
    private static final Pattern PTZ_HEX = Pattern.compile("[0-9a-fA-F]{16}");
    private static final int MAX_XML_DEPTH = 32;
    private static final int MAX_XML_ELEMENTS = 2_048;
    private static final int MAX_COMMAND_LENGTH = 32;
    private static final int MAX_NAME_LENGTH = 256;
    private static final int MAX_STATUS_LENGTH = 16;
    private static final int MAX_DEVICE_TIME_LENGTH = 64;
    private static final int MAX_ALARM_DESCRIPTION_LENGTH = 1_024;
    private static final int MAX_PRESET_ITEMS = 255;
    private static final int DEFAULT_MAX_RECORD_ITEMS = 10_000;
    private static final int DEFAULT_MAX_MOBILE_POSITION_ITEMS = 1_000;
    private static final DateTimeFormatter RECORD_QUERY_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss", Locale.ROOT);
    private static final String INVALID_XML = "Invalid GB28181 XML";

    private GbXml() {
    }

    public static Message parse(byte[] body, int maxMessageBytes, int maxCatalogItems) {
        return parse(body, maxMessageBytes, maxCatalogItems, DEFAULT_MAX_RECORD_ITEMS,
                DEFAULT_MAX_MOBILE_POSITION_ITEMS);
    }

    public static Message parse(byte[] body, int maxMessageBytes, int maxCatalogItems, int maxRecordItems) {
        return parse(body, maxMessageBytes, maxCatalogItems, maxRecordItems, DEFAULT_MAX_MOBILE_POSITION_ITEMS);
    }

    public static Message parse(byte[] body, int maxMessageBytes, int maxCatalogItems, int maxRecordItems,
                                int maxMobilePositionItems) {
        if (body == null || body.length == 0 || maxMessageBytes <= 0
                || body.length > maxMessageBytes || maxCatalogItems < 0 || maxRecordItems < 0
                || maxMobilePositionItems < 0) {
            throw invalid();
        }

        Document document = parseDocument(body);
        Element root = document.getDocumentElement();
        enforceStructureLimits(root);

        String kind = nodeName(root);
        String command = requiredText(root, "CmdType", MAX_COMMAND_LENGTH);
        int sn = positiveInt(requiredText(root, "SN", 10));
        String deviceId = requiredText(root, "DeviceID", 20);

        if ("Notify".equals(kind) && "Alarm".equals(command)) {
            requireMatches(deviceId, ALARM_DEVICE_ID);
            return parseAlarm(root, kind, command, sn, deviceId);
        }
        requireMatches(deviceId, DEVICE_ID);

        if ("Notify".equals(kind) && "Catalog".equals(command)) {
            return parseCatalogNotification(root, kind, command, sn, deviceId, maxCatalogItems);
        }
        if ("Notify".equals(kind) && "MobilePosition".equals(command)) {
            return parseMobilePosition(root, kind, command, sn, deviceId, maxMobilePositionItems);
        }

        if ("Notify".equals(kind) && "Keepalive".equals(command)) {
            String status = requiredText(root, "Status", MAX_STATUS_LENGTH);
            if (!"OK".equals(status)) {
                throw invalid();
            }
            return new Message(kind, command, sn, deviceId, status, 0, List.of());
        }
        if ("Notify".equals(kind) && "MediaStatus".equals(command)) {
            String notifyType = requiredText(root, "NotifyType", 3);
            if (!"121".equals(notifyType)) {
                throw invalid();
            }
            return new Message(kind, command, sn, deviceId, notifyType, 0, List.of());
        }
        if ("Response".equals(kind) && "Catalog".equals(command)) {
            return parseCatalog(root, kind, command, sn, deviceId, maxCatalogItems);
        }
        if ("Response".equals(kind) && "RecordInfo".equals(command)) {
            return parseRecordInfo(root, kind, command, sn, deviceId, maxRecordItems);
        }
        if ("Response".equals(kind) && "PresetQuery".equals(command)) {
            return parsePresets(root, kind, command, sn, deviceId);
        }
        if ("Response".equals(kind) && "HomePositionQuery".equals(command)) {
            String result = enumText(root, "Result", false, "OK", "ERROR");
            return homePositionMessage(kind, command, sn, deviceId, result, parseHomePosition(root, deviceId));
        }
        if ("Response".equals(kind) && "PTZPosition".equals(command)) {
            Element info = requiredElement(root, "PTZPosInfo");
            requireDirectOccurrence(root, "PTZPosInfo", true);
            requireDirectOccurrence(info, "Pan", false);
            requireDirectOccurrence(info, "Tilt", false);
            requireDirectOccurrence(info, "Zoom", false);
            String result = enumText(root, "Result", false, "OK", "ERROR");
            Double pan = optionalCoordinate(info, "Pan", -360, 360);
            Double tilt = optionalCoordinate(info, "Tilt", -360, 360);
            Double zoom = optionalCoordinate(info, "Zoom", 0, Double.POSITIVE_INFINITY);
            return new Message(kind, command, sn, deviceId, null, 0, List.of(), result,
                    null, null, List.of(), null, List.of(), null, null, null,
                    new PtzPosition(deviceId, pan, tilt, zoom));
        }
        if ("Response".equals(kind)
                && ("DeviceInfo".equals(command) || "DeviceStatus".equals(command)
                || "DeviceControl".equals(command))) {
            String result = enumText(root, "Result", true, "OK", "ERROR");
            boolean success = "OK".equals(result);
            DeviceInfo info = null;
            DeviceStatus deviceStatus = null;
            if ("DeviceInfo".equals(command)) {
                DeviceInfo parsed = parseDeviceInfo(root, deviceId);
                info = success ? parsed : null;
            } else if ("DeviceStatus".equals(command)) {
                DeviceStatus parsed = parseDeviceStatus(root, deviceId, success);
                deviceStatus = success ? parsed : null;
            }
            return new Message(kind, command, sn, deviceId, null, 0, List.of(),
                    result, info, deviceStatus);
        }
        throw invalid();
    }

    private static Message homePositionMessage(String kind, String command, int sn, String deviceId,
                                                String result, HomePosition home) {
        return new Message(kind, command, sn, deviceId, null, 0, List.of(), result,
                null, null, List.of(), null, List.of(), null, null, home);
    }

    private static HomePosition parseHomePosition(Element root, String deviceId) {
        requireDirectOccurrence(root, "HomePosition", false);
        Element home = uniqueDirectChild(root, "HomePosition");
        for (String field : List.of("Enabled", "ResetTime", "PresetIndex")) {
            if (countDescendants(root, field) != (home == null ? 0 : countDescendants(home, field))) {
                throw invalid();
            }
        }
        if (home == null) return new HomePosition(deviceId, null, null, null);
        requireDirectOccurrence(home, "Enabled", true);
        requireDirectOccurrence(home, "ResetTime", false);
        requireDirectOccurrence(home, "PresetIndex", false);
        String enabledText = requiredText(home, "Enabled", 2);
        int enabled = nonNegativeInt(enabledText);
        if (enabled > 1) throw invalid();
        String resetText = uniqueDirectChild(home, "ResetTime") == null ? null : requiredText(home, "ResetTime", 10);
        String presetText = uniqueDirectChild(home, "PresetIndex") == null ? null : requiredText(home, "PresetIndex", 10);
        Integer reset = resetText == null ? null : nonNegativeInt(resetText);
        Integer preset = presetText == null ? null : nonNegativeInt(presetText);
        if (preset != null && preset > 255) throw invalid();
        return new HomePosition(deviceId, enabled == 1, reset, preset);
    }

    public static byte[] catalogQuery(String deviceId, int sn) {
        return query("Catalog", deviceId, sn);
    }

    public static byte[] mobilePositionSubscription(String deviceId, int sn,
                                                    GbMobilePositionSubscriptionRequest request) {
        requireMatches(deviceId, DEVICE_ID);
        Objects.requireNonNull(request, "request");
        if (sn <= 0) {
            throw new IllegalArgumentException("SN must be positive");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Query><CmdType>MobilePosition</CmdType><SN>" + sn + "</SN><DeviceID>" + deviceId
                + "</DeviceID><Interval>" + request.interval().getSeconds() + "</Interval></Query>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] presetQuery(String channelId, int sn) {
        requireMatches(channelId, DEVICE_ID);
        if (sn <= 0) {
            throw new IllegalArgumentException("SN must be positive");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Query>\r\n"
                + "<CmdType>PresetQuery</CmdType>\r\n"
                + "<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>" + channelId + "</DeviceID>\r\n"
                + "</Query>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] alarmResponse(String deviceId, int sn) {
        requireMatches(deviceId, ALARM_DEVICE_ID);
        if (sn <= 0) {
            throw new IllegalArgumentException("SN must be positive");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Response>\r\n<CmdType>Alarm</CmdType>\r\n<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>" + deviceId + "</DeviceID>\r\n<Result>OK</Result>\r\n</Response>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] alarmSubscribe(String targetId, int sn, GbAlarmSubscriptionRequest request) {
        requireMatches(targetId, ALARM_DEVICE_ID);
        Objects.requireNonNull(request, "request");
        if (sn <= 0) {
            throw new IllegalArgumentException("SN must be positive");
        }
        String methods = request.methods().isEmpty()
                ? "0"
                : request.methods().stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining("/"));
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n")
                .append("<Query><CmdType>Alarm</CmdType><SN>").append(sn)
                .append("</SN><DeviceID>").append(targetId)
                .append("</DeviceID><StartAlarmPriority>").append(request.startPriority())
                .append("</StartAlarmPriority><EndAlarmPriority>").append(request.endPriority())
                .append("</EndAlarmPriority><AlarmMethod>").append(methods).append("</AlarmMethod>");
        if (request.type() != null) {
            xml.append("<AlarmType>").append(request.type()).append("</AlarmType>");
        }
        if (request.startTime() != null) {
            xml.append("<StartAlarmTime>").append(RECORD_QUERY_TIME.format(request.startTime()))
                    .append("</StartAlarmTime><EndAlarmTime>").append(RECORD_QUERY_TIME.format(request.endTime()))
                    .append("</EndAlarmTime>");
        }
        xml.append("</Query>");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] catalogSubscribe(String owner, int sn, GbCatalogSubscriptionRequest request) {
        requireMatches(owner, DEVICE_ID);
        Objects.requireNonNull(request, "request");
        if (sn <= 0) {
            throw new IllegalArgumentException("SN must be positive");
        }
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n")
                .append("<Query><CmdType>Catalog</CmdType><SN>").append(sn)
                .append("</SN><DeviceID>").append(owner).append("</DeviceID>");
        if (request.startTime() != null) {
            xml.append("<StartTime>").append(RECORD_QUERY_TIME.format(request.startTime())).append("</StartTime>");
        }
        if (request.endTime() != null) {
            xml.append("<EndTime>").append(RECORD_QUERY_TIME.format(request.endTime())).append("</EndTime>");
        }
        return xml.append("</Query>").toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] recordQuery(String channelId, int sn, RecordQuery query) {
        requireMatches(channelId, DEVICE_ID);
        Objects.requireNonNull(query, "query");
        if (sn <= 0) {
            throw new IllegalArgumentException("SN must be positive");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<Query>"
                + "<CmdType>RecordInfo</CmdType><SN>" + sn + "</SN><DeviceID>" + channelId + "</DeviceID>"
                + "<StartTime>" + RECORD_QUERY_TIME.format(query.startTime()) + "</StartTime>"
                + "<EndTime>" + RECORD_QUERY_TIME.format(query.endTime()) + "</EndTime>"
                + "<Type>" + query.type().name().toLowerCase(Locale.ROOT) + "</Type></Query>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] query(String command, String deviceId, int sn) {
        if (!"Catalog".equals(command) && !"DeviceInfo".equals(command) && !"DeviceStatus".equals(command)
                && !"HomePositionQuery".equals(command) && !"PTZPosition".equals(command)) {
            throw new IllegalArgumentException("Unsupported GB28181 query command");
        }
        requireMatches(deviceId, DEVICE_ID);
        if (sn <= 0) {
            throw new IllegalArgumentException("SN must be positive");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Query>\r\n"
                + "<CmdType>" + command + "</CmdType>\r\n"
                + "<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>" + deviceId + "</DeviceID>\r\n"
                + "</Query>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] ptzControl(String channelId, int sn, String ptzHex) {
        requireMatches(channelId, DEVICE_ID);
        if (sn <= 0) {
            throw new IllegalArgumentException("SN must be positive");
        }
        if (ptzHex == null || !PTZ_HEX.matcher(ptzHex).matches()) {
            throw new IllegalArgumentException("PTZ command must contain 16 hexadecimal digits");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Control>\r\n"
                + "<CmdType>DeviceControl</CmdType>\r\n"
                + "<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>" + channelId + "</DeviceID>\r\n"
                + "<PTZCmd>" + ptzHex.toUpperCase(Locale.ROOT) + "</PTZCmd>\r\n"
                + "</Control>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] alarmReset(String targetId, int sn, GbAlarmResetCommand command) {
        Objects.requireNonNull(command, "command");
        String methods = command.methods().isEmpty()
                ? "0"
                : command.methods().stream().sorted().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining("/"));
        StringBuilder operation = new StringBuilder("<AlarmCmd>ResetAlarm</AlarmCmd>\r\n")
                .append("<Info>\r\n<AlarmMethod>").append(methods).append("</AlarmMethod>\r\n");
        if (command.type() != null) {
            operation.append("<AlarmType>").append(command.type()).append("</AlarmType>\r\n");
        }
        return deviceControl(targetId, sn, operation.append("</Info>").toString(), true);
    }

    public static byte[] keyFrameRequest(String channelId, int sn) {
        return deviceControl(channelId, sn, "<IFrameCmd>Send</IFrameCmd>");
    }

    public static byte[] teleBoot(String deviceId, int sn) {
        return deviceControl(deviceId, sn, "<TeleBoot>" + GbTeleBootCommand.BOOT.wireValue() + "</TeleBoot>");
    }

    public static byte[] recordControl(String channelId, int sn, GbRecordControlCommand command) {
        Objects.requireNonNull(command, "Record control command");
        return deviceControl(channelId, sn, "<RecordCmd>" + command.wireValue() + "</RecordCmd>");
    }

    public static byte[] guard(String targetId, int sn, GbGuardCommand command) {
        Objects.requireNonNull(command, "Guard command");
        return deviceControl(targetId, sn, "<GuardCmd>" + command.wireValue() + "</GuardCmd>");
    }

    public static byte[] dragZoomIn(String channelId, int sn, GbDragZoomCommand command) {
        return dragZoom(channelId, sn, "DragZoomIn", command);
    }

    public static byte[] dragZoomOut(String channelId, int sn, GbDragZoomCommand command) {
        return dragZoom(channelId, sn, "DragZoomOut", command);
    }

    private static byte[] dragZoom(String channelId, int sn, String operation, GbDragZoomCommand command) {
        Objects.requireNonNull(command, "Drag zoom command");
        String body = "<" + operation + ">\r\n"
                + "<Length>" + command.length() + "</Length>\r\n"
                + "<Width>" + command.width() + "</Width>\r\n"
                + "<MidPointX>" + command.midPointX() + "</MidPointX>\r\n"
                + "<MidPointY>" + command.midPointY() + "</MidPointY>\r\n"
                + "<LengthX>" + command.lengthX() + "</LengthX>\r\n"
                + "<LengthY>" + command.lengthY() + "</LengthY>\r\n"
                + "</" + operation + ">";
        return deviceControl(channelId, sn, body);
    }

    private static byte[] deviceControl(String targetId, int sn, String operation) {
        return deviceControl(targetId, sn, operation, false);
    }

    private static byte[] deviceControl(String targetId, int sn, String operation, boolean allowAlarmCenter) {
        requireMatches(targetId, allowAlarmCenter ? ALARM_DEVICE_ID : DEVICE_ID);
        if (sn <= 0) {
            throw new IllegalArgumentException("SN must be positive");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Control>\r\n"
                + "<CmdType>DeviceControl</CmdType>\r\n"
                + "<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>" + targetId + "</DeviceID>\r\n"
                + operation + "\r\n"
                + "</Control>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static DeviceInfo parseDeviceInfo(Element root, String deviceId) {
        String name = optionalText(root, "DeviceName", MAX_NAME_LENGTH);
        String manufacturer = optionalText(root, "Manufacturer", MAX_NAME_LENGTH);
        String model = optionalText(root, "Model", MAX_NAME_LENGTH);
        String firmware = optionalText(root, "Firmware", MAX_NAME_LENGTH);
        String channels = optionalText(root, "Channel", 10);
        return new DeviceInfo(deviceId, name, manufacturer, model, firmware,
                channels == null ? null : nonNegativeInt(channels));
    }

    private static Message parseAlarm(Element root, String kind, String command, int sn, String deviceId) {
        int priority = positiveInt(requiredText(root, "AlarmPriority", 1));
        if (priority > 4) {
            throw invalid();
        }
        int method = positiveInt(requiredText(root, "AlarmMethod", 1));
        if (method > 7) {
            throw invalid();
        }
        String time = requiredText(root, "AlarmTime", MAX_DEVICE_TIME_LENGTH);
        recordTime(time);
        String description = optionalText(root, "AlarmDescription", MAX_ALARM_DESCRIPTION_LENGTH);
        Double longitude = optionalCoordinate(root, "Longitude", -180.0, 180.0);
        Double latitude = optionalCoordinate(root, "Latitude", -90.0, 90.0);

        Integer type = null;
        Integer eventType = null;
        Element info = uniqueDirectChild(root, "Info");
        if (info != null) {
            type = positiveInt(requiredText(info, "AlarmType", 10));
            Element typeParam = uniqueDirectChild(info, "AlarmTypeParam");
            if (typeParam != null) {
                String event = optionalText(typeParam, "EventType", 1);
                if (event != null) {
                    eventType = positiveInt(event);
                    if (eventType > 2) {
                        throw invalid();
                    }
                }
            }
        }
        GbAlarm alarm = new GbAlarm(deviceId, sn, priority, method, time, description,
                longitude, latitude, type, eventType);
        return new Message(kind, command, sn, deviceId, null, 0, List.of(),
                null, null, null, List.of(), alarm);
    }

    private static Double optionalCoordinate(Element root, String name, double minimum, double maximum) {
        String value = optionalText(root, name, 64);
        if (value == null) {
            return null;
        }
        if (!XML_DOUBLE.matcher(value).matches()) {
            throw invalid();
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum) {
                throw invalid();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static DeviceStatus parseDeviceStatus(Element root, String deviceId, boolean success) {
        String online = enumText(root, "Online", success, "ONLINE", "OFFLINE");
        String status = enumText(root, "Status", success, "OK", "ERROR");
        String encode = enumText(root, "Encode", false, "ON", "OFF");
        String record = enumText(root, "Record", false, "ON", "OFF");
        String time = optionalText(root, "DeviceTime", MAX_DEVICE_TIME_LENGTH);
        if (time != null) {
            try {
                var calendar = DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(time);
                if (!DatatypeConstants.DATETIME.equals(calendar.getXMLSchemaType()) || !calendar.isValid()) {
                    throw invalid();
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                throw invalid();
            }
        }
        return new DeviceStatus(deviceId, online, status, encode, record, time);
    }

    private static String enumText(Element root, String name, boolean required, String first, String second) {
        String value = required ? requiredText(root, name, MAX_STATUS_LENGTH)
                : optionalText(root, name, MAX_STATUS_LENGTH);
        if (value != null && !first.equals(value) && !second.equals(value)) {
            throw invalid();
        }
        return value;
    }

    private static Message parseCatalog(Element root, String kind, String command, int sn,
                                        String deviceId, int maxCatalogItems) {
        int total = nonNegativeInt(requiredText(root, "SumNum", 10));
        if (total > maxCatalogItems) {
            throw invalid();
        }

        Element deviceList = requiredElement(root, "DeviceList");
        String declaredCount = deviceList.getAttribute("Num");
        if (!deviceList.hasAttribute("Num") || declaredCount.isBlank()) {
            throw invalid();
        }
        int expectedItems = nonNegativeInt(declaredCount.trim());

        List<CatalogItem> items = new ArrayList<>();
        NodeList children = deviceList.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && "Item".equals(nodeName(element))) {
                if (items.size() >= maxCatalogItems) {
                    throw invalid();
                }
                items.add(parseCatalogItem(element));
            }
        }
        if (expectedItems != items.size() || items.size() > total) {
            throw invalid();
        }
        return new Message(kind, command, sn, deviceId, null, total, items);
    }

    private static Message parseCatalogNotification(Element root, String kind, String command, int sn,
                                                    String deviceId, int maxCatalogItems) {
        int total = nonNegativeInt(requiredText(root, "SumNum", 10));
        if (total > maxCatalogItems) {
            throw invalid();
        }
        Element deviceList = uniqueDirectChild(root, "DeviceList");
        if (deviceList == null) {
            if (total != 0) {
                throw invalid();
            }
            return catalogNotificationMessage(kind, command, sn, deviceId, total, List.of());
        }
        if (!deviceList.hasAttribute("Num") || deviceList.getAttribute("Num").isBlank()) {
            throw invalid();
        }
        int expectedItems = nonNegativeInt(deviceList.getAttribute("Num").trim());
        if (expectedItems > maxCatalogItems) {
            throw invalid();
        }
        List<GbCatalogNotification.Entry> entries = new ArrayList<>();
        boolean hasEvent = false;
        boolean hasDirectoryEntry = false;
        NodeList children = deviceList.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element && "Item".equals(nodeName(element))) {
                if (entries.size() >= maxCatalogItems) {
                    throw invalid();
                }
                GbCatalogNotification.Type type = parseCatalogEvent(element);
                hasEvent |= type != null;
                hasDirectoryEntry |= type == null;
                CatalogItem item = parseCatalogItem(element);
                if ((type == GbCatalogNotification.Type.ADD || type == GbCatalogNotification.Type.UPDATE)
                        && (item.name() == null || item.status() == null)) {
                    throw invalid();
                }
                entries.add(new GbCatalogNotification.Entry(item, type));
            }
        }
        if (entries.size() != expectedItems || (hasEvent && hasDirectoryEntry)
                || (hasEvent ? total != entries.size() : entries.size() > total)) {
            throw invalid();
        }
        return catalogNotificationMessage(kind, command, sn, deviceId, total, entries);
    }

    private static GbCatalogNotification.Type parseCatalogEvent(Element item) {
        Element direct = uniqueDirectChild(item, "Event");
        int eventElements = 0;
        NodeList descendants = item.getElementsByTagNameNS("*", "Event");
        eventElements += descendants.getLength();
        NodeList unqualified = item.getElementsByTagName("Event");
        if (descendants.getLength() == 0) {
            eventElements += unqualified.getLength();
        }
        if (eventElements != (direct == null ? 0 : 1)) {
            throw invalid();
        }
        if (direct == null) {
            return null;
        }
        String value = requiredText(item, "Event", MAX_STATUS_LENGTH);
        try {
            return GbCatalogNotification.Type.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static Message catalogNotificationMessage(String kind, String command, int sn, String deviceId,
                                                      int total, List<GbCatalogNotification.Entry> entries) {
        var notification = new GbCatalogNotification(deviceId, sn, total, entries);
        return new Message(kind, command, sn, deviceId, null, total, List.of(),
                null, null, null, List.of(), null, List.of(), notification);
    }

    private static Message parseMobilePosition(Element root, String kind, String command, int sn,
                                               String deviceId, int maxMobilePositionItems) {
        requireDirectOccurrence(root, "Time", true);
        requireDirectOccurrence(root, "SumNum", true);
        requireDirectOccurrence(root, "DeviceList", false);
        String time = requiredText(root, "Time", MAX_DEVICE_TIME_LENGTH);
        xmlDateTime(time);
        int total = nonNegativeInt(requiredText(root, "SumNum", 10));
        if (total > maxMobilePositionItems) {
            throw invalid();
        }
        Element deviceList = uniqueDirectChild(root, "DeviceList");
        rejectPositionFieldsOutsideList(root, deviceList);
        if (deviceList == null) {
            if (total != 0) {
                throw invalid();
            }
            return mobilePositionMessage(kind, command, sn, deviceId, time, total, List.of());
        }
        if (!deviceList.hasAttribute("Num") || deviceList.getAttribute("Num").isBlank()) {
            throw invalid();
        }
        int expectedItems = nonNegativeInt(deviceList.getAttribute("Num").trim());
        if (expectedItems > total || expectedItems > maxMobilePositionItems) {
            throw invalid();
        }
        List<GbMobilePosition> positions = new ArrayList<>();
        NodeList children = deviceList.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child) {
                if ("Item".equals(nodeName(child))) {
                    if (positions.size() >= maxMobilePositionItems) {
                        throw invalid();
                    }
                    positions.add(parseMobilePositionItem(child));
                } else if (containsPositionItemField(child)) {
                    throw invalid();
                }
            }
        }
        if (positions.size() != expectedItems || countDescendants(deviceList, "Item") != positions.size()) {
            throw invalid();
        }
        return mobilePositionMessage(kind, command, sn, deviceId, time, total, positions);
    }

    private static GbMobilePosition parseMobilePositionItem(Element item) {
        for (String name : List.of("DeviceID", "CaptureTime", "Longitude", "Latitude")) {
            requireDirectOccurrence(item, name, true);
        }
        for (String name : List.of("Speed", "Direction", "Altitude", "Height")) {
            requireDirectOccurrence(item, name, false);
        }
        String itemId = requiredText(item, "DeviceID", 20);
        requireMatches(itemId, DEVICE_ID);
        String captureTime = requiredText(item, "CaptureTime", MAX_DEVICE_TIME_LENGTH);
        xmlDateTime(captureTime);
        double longitude = decimal(requiredText(item, "Longitude", 64), -180, 180, true);
        double latitude = decimal(requiredText(item, "Latitude", 64), -90, 90, true);
        Double speed = optionalDecimal(item, "Speed", 0, Double.POSITIVE_INFINITY, true);
        Double direction = optionalDecimal(item, "Direction", 0, 360, false);
        Double altitude = optionalDecimal(item, "Altitude", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true);
        Double height = optionalDecimal(item, "Height", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true);
        return new GbMobilePosition(itemId, captureTime, longitude, latitude, speed, direction, altitude, height);
    }

    private static void rejectPositionFieldsOutsideList(Element root, Element deviceList) {
        NodeList children = root.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child && child != deviceList
                    && containsPositionItemField(child)) {
                throw invalid();
            }
        }
    }

    private static boolean containsPositionItemField(Element element) {
        if (List.of("CaptureTime", "Longitude", "Latitude", "Speed", "Direction", "Altitude", "Height")
                .contains(nodeName(element))) {
            return true;
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child && containsPositionItemField(child)) {
                return true;
            }
        }
        return false;
    }

    private static Double optionalDecimal(Element parent, String name, double minimum, double maximum,
                                          boolean inclusiveMaximum) {
        if (uniqueDirectChild(parent, name) == null) {
            return null;
        }
        return decimal(requiredText(parent, name, 64), minimum, maximum, inclusiveMaximum);
    }

    private static double decimal(String value, double minimum, double maximum, boolean inclusiveMaximum) {
        if (!XML_DOUBLE.matcher(value).matches()) {
            throw invalid();
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed < minimum
                    || (inclusiveMaximum ? parsed > maximum : parsed >= maximum)) {
                throw invalid();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static Message mobilePositionMessage(String kind, String command, int sn, String deviceId,
                                                 String time, int total, List<GbMobilePosition> positions) {
        var notification = new GbMobilePositionNotification(deviceId, sn, time, total, positions);
        return new Message(kind, command, sn, deviceId, null, total, List.of(),
                null, null, null, List.of(), null, List.of(), null, notification);
    }

    private static CatalogItem parseCatalogItem(Element item) {
        String itemId = requiredText(item, "DeviceID", 20);
        requireMatches(itemId, CATALOG_ID);
        String name = optionalText(item, "Name", MAX_NAME_LENGTH);
        String parentId = optionalText(item, "ParentID", 20);
        if (parentId != null) {
            requireMatches(parentId, CATALOG_ID);
        }
        String status = optionalText(item, "Status", MAX_STATUS_LENGTH);
        if (status != null && !"ON".equals(status) && !"OFF".equals(status)) {
            throw invalid();
        }
        return new CatalogItem(itemId, name, parentId, status);
    }

    private static Message parseRecordInfo(Element root, String kind, String command, int sn,
                                           String deviceId, int maxRecordItems) {
        requiredText(root, "Name", MAX_NAME_LENGTH);
        int total = nonNegativeInt(requiredText(root, "SumNum", 10));
        if (total > maxRecordItems) {
            throw invalid();
        }
        Element recordList = requiredElement(root, "RecordList");
        int expectedItems = nonNegativeInt(recordList.getAttribute("Num").trim());
        if (expectedItems > total || expectedItems > maxRecordItems) {
            throw invalid();
        }
        List<RecordItem> records = new ArrayList<>();
        NodeList children = recordList.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element && "Item".equals(nodeName(element))) {
                if (records.size() >= expectedItems) {
                    throw invalid();
                }
                records.add(parseRecordItem(element, deviceId));
            }
        }
        if (records.size() != expectedItems) {
            throw invalid();
        }
        return new Message(kind, command, sn, deviceId, null, total, List.of(), null, null, null, records);
    }

    private static Message parsePresets(Element root, String kind, String command, int sn, String deviceId) {
        int total = nonNegativeInt(requiredText(root, "SumNum", 10));
        if (total > MAX_PRESET_ITEMS) {
            throw invalid();
        }

        Element presetList = requiredElement(root, "PresetList");
        if (!presetList.hasAttribute("Num") || presetList.getAttribute("Num").isBlank()) {
            throw invalid();
        }
        int expectedItems = nonNegativeInt(presetList.getAttribute("Num").trim());
        if (expectedItems > MAX_PRESET_ITEMS || expectedItems > total) {
            throw invalid();
        }

        List<PresetItem> presets = new ArrayList<>();
        NodeList children = presetList.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element element && "Item".equals(nodeName(element))) {
                if (presets.size() >= expectedItems) {
                    throw invalid();
                }
                presets.add(parsePresetItem(element));
            }
        }
        if (presets.size() != expectedItems) {
            throw invalid();
        }
        return new Message(kind, command, sn, deviceId, null, total, List.of(),
                null, null, null, List.of(), null, presets);
    }

    private static PresetItem parsePresetItem(Element item) {
        String presetId = requiredText(item, "PresetID", 64);
        String presetName = requiredTextAllowEmpty(item, "PresetName", MAX_NAME_LENGTH);
        return new PresetItem(presetId, presetName);
    }

    private static RecordItem parseRecordItem(Element item, String deviceId) {
        String itemId = requiredText(item, "DeviceID", 20);
        if (!deviceId.equals(itemId)) {
            throw invalid();
        }
        String name = requiredText(item, "Name", MAX_NAME_LENGTH);
        String filePath = optionalText(item, "FilePath", 1024);
        String address = optionalText(item, "Address", MAX_NAME_LENGTH);
        String startTime = optionalText(item, "StartTime", MAX_DEVICE_TIME_LENGTH);
        String endTime = optionalText(item, "EndTime", MAX_DEVICE_TIME_LENGTH);
        XMLGregorianCalendar start = startTime == null ? null : recordTime(startTime);
        XMLGregorianCalendar end = endTime == null ? null : recordTime(endTime);
        if (start != null && end != null) {
            int order = start.compare(end);
            // A missing time zone may make the order indeterminate; never infer the host time zone.
            if (order == DatatypeConstants.GREATER) {
                throw invalid();
            }
        }
        int secrecy = nonNegativeInt(requiredText(item, "Secrecy", 10));
        if (secrecy > 1) {
            throw invalid();
        }
        String type = optionalText(item, "Type", MAX_STATUS_LENGTH);
        if (type != null && !"time".equals(type) && !"alarm".equals(type) && !"manual".equals(type)) {
            throw invalid();
        }
        String recorderId = optionalText(item, "RecorderID", 128);
        String fileSize = optionalText(item, "FileSize", 19);
        return new RecordItem(itemId, name, filePath, address, startTime, endTime, secrecy, type,
                recorderId, fileSize == null ? null : nonNegativeLong(fileSize));
    }

    private static XMLGregorianCalendar recordTime(String value) {
        return xmlDateTime(value);
    }

    private static XMLGregorianCalendar xmlDateTime(String value) {
        try {
            XMLGregorianCalendar calendar = DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(value);
            if (!DatatypeConstants.DATETIME.equals(calendar.getXMLSchemaType()) || !calendar.isValid()) {
                throw invalid();
            }
            return calendar;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid();
        }
    }

    private static long nonNegativeLong(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                throw invalid();
            }
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static Document parseDocument(byte[] body) {
        try {
            DocumentBuilder builder = newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(body));
        } catch (ParserConfigurationException | SAXException | java.io.IOException | RuntimeException exception) {
            throw invalid();
        }
    }

    private static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", MAX_XML_DEPTH);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new SilentErrorHandler());
        return builder;
    }

    private static void enforceStructureLimits(Element root) {
        if (root == null) {
            throw invalid();
        }
        int elements = 0;
        List<NodeDepth> pending = new ArrayList<>();
        pending.add(new NodeDepth(root, 1));
        while (!pending.isEmpty()) {
            NodeDepth current = pending.remove(pending.size() - 1);
            if (current.depth() > MAX_XML_DEPTH || ++elements > MAX_XML_ELEMENTS) {
                throw invalid();
            }
            NodeList children = current.node().getChildNodes();
            for (int index = 0; index < children.getLength(); index++) {
                if (children.item(index) instanceof Element child) {
                    pending.add(new NodeDepth(child, current.depth() + 1));
                }
            }
        }
    }

    private static Element requiredElement(Element parent, String name) {
        Element element = uniqueDirectChild(parent, name);
        if (element == null) {
            throw invalid();
        }
        return element;
    }

    private static String requiredText(Element parent, String name, int maxLength) {
        String text = text(parent, name, maxLength, true);
        if (text == null || text.isBlank()) {
            throw invalid();
        }
        return text;
    }

    private static String optionalText(Element parent, String name, int maxLength) {
        return text(parent, name, maxLength, false);
    }

    private static String requiredTextAllowEmpty(Element parent, String name, int maxLength) {
        Element element = requiredElement(parent, name);
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element) {
                throw invalid();
            }
        }
        String value = element.getTextContent().trim();
        if (value.length() > maxLength) {
            throw invalid();
        }
        return value;
    }

    private static String text(Element parent, String name, int maxLength, boolean required) {
        Element element = uniqueDirectChild(parent, name);
        if (element == null) {
            if (required) {
                throw invalid();
            }
            return null;
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element) {
                throw invalid();
            }
        }
        String value = element.getTextContent().trim();
        if (value.isEmpty()) {
            return required ? failText() : null;
        }
        if (value.length() > maxLength) {
            throw invalid();
        }
        return value;
    }

    private static String failText() {
        throw invalid();
    }

    private static Element uniqueDirectChild(Element parent, String name) {
        Element found = null;
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(nodeName(element))) {
                if (found != null) {
                    throw invalid();
                }
                found = element;
            }
        }
        return found;
    }

    private static void requireDirectOccurrence(Element parent, String name, boolean required) {
        Element direct = uniqueDirectChild(parent, name);
        int occurrences = countDescendants(parent, name);
        if (occurrences != (direct == null ? 0 : 1) || (required && direct == null)) {
            throw invalid();
        }
    }

    private static int countDescendants(Element parent, String name) {
        int count = 0;
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child) {
                if (name.equals(nodeName(child))) {
                    count++;
                }
                count += countDescendants(child, name);
            }
        }
        return count;
    }

    private static int positiveInt(String value) {
        int parsed = nonNegativeInt(value);
        if (parsed == 0) {
            throw invalid();
        }
        return parsed;
    }

    private static int nonNegativeInt(String value) {
        if (value.isEmpty() || value.length() > 10) {
            throw invalid();
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                throw invalid();
            }
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static void requireMatches(String value, Pattern pattern) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid GB28181 identifier");
        }
    }

    private static String nodeName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(INVALID_XML);
    }

    public record Message(String kind, String command, int sn, String deviceId,
                          String status, int total, List<CatalogItem> items, String result,
                          DeviceInfo deviceInfo, DeviceStatus deviceStatus, List<RecordItem> records,
                          GbAlarm alarm, List<PresetItem> presets, GbCatalogNotification catalogNotification,
                          GbMobilePositionNotification mobilePositionNotification, HomePosition homePosition,
                          PtzPosition ptzPosition) {
                          
        public Message(String kind, String command, int sn, String deviceId,
                       String status, int total, List<CatalogItem> items, String result,
                       DeviceInfo deviceInfo, DeviceStatus deviceStatus, List<RecordItem> records,
                       GbAlarm alarm, List<PresetItem> presets, GbCatalogNotification catalogNotification) {
            this(kind, command, sn, deviceId, status, total, items, result,
                    deviceInfo, deviceStatus, records, alarm, presets, catalogNotification, null, null, null);
        }

        public Message(String kind, String command, int sn, String deviceId,
                       String status, int total, List<CatalogItem> items, String result,
                       DeviceInfo deviceInfo, DeviceStatus deviceStatus, List<RecordItem> records,
                       GbAlarm alarm, List<PresetItem> presets, GbCatalogNotification catalogNotification,
                       GbMobilePositionNotification mobilePositionNotification) {
            this(kind, command, sn, deviceId, status, total, items, result, deviceInfo, deviceStatus,
                    records, alarm, presets, catalogNotification, mobilePositionNotification, null, null);
        }

        public Message(String kind, String command, int sn, String deviceId, String status, int total,
                       List<CatalogItem> items, String result, DeviceInfo deviceInfo, DeviceStatus deviceStatus,
                       List<RecordItem> records, GbAlarm alarm, List<PresetItem> presets,
                       GbCatalogNotification catalogNotification, GbMobilePositionNotification mobilePositionNotification,
                       HomePosition homePosition) {
            this(kind, command, sn, deviceId, status, total, items, result, deviceInfo, deviceStatus, records,
                    alarm, presets, catalogNotification, mobilePositionNotification, homePosition, null);
        }

        public Message(String kind, String command, int sn, String deviceId,
                       String status, int total, List<CatalogItem> items, String result,
                       DeviceInfo deviceInfo, DeviceStatus deviceStatus, List<RecordItem> records,
                       GbAlarm alarm, List<PresetItem> presets) {
            this(kind, command, sn, deviceId, status, total, items, result,
                    deviceInfo, deviceStatus, records, alarm, presets, null, null, null);
        }

        public Message(String kind, String command, int sn, String deviceId,
                       String status, int total, List<CatalogItem> items, String result,
                       DeviceInfo deviceInfo, DeviceStatus deviceStatus, List<RecordItem> records,
                       GbAlarm alarm) {
            this(kind, command, sn, deviceId, status, total, items, result,
                    deviceInfo, deviceStatus, records, alarm, List.of());
        }

        public Message(String kind, String command, int sn, String deviceId,
                       String status, int total, List<CatalogItem> items, String result,
                       DeviceInfo deviceInfo, DeviceStatus deviceStatus, List<RecordItem> records) {
            this(kind, command, sn, deviceId, status, total, items, result,
                    deviceInfo, deviceStatus, records, null);
        }

        public Message(String kind, String command, int sn, String deviceId,
                       String status, int total, List<CatalogItem> items, String result,
                       DeviceInfo deviceInfo, DeviceStatus deviceStatus) {
            this(kind, command, sn, deviceId, status, total, items, result, deviceInfo, deviceStatus, List.of());
        }

        public Message(String kind, String command, int sn, String deviceId,
                       String status, int total, List<CatalogItem> items) {
            this(kind, command, sn, deviceId, status, total, items, null, null, null);
        }

        public Message {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(deviceId, "deviceId");
            items = List.copyOf(items);
            records = List.copyOf(records);
            presets = List.copyOf(presets);
        }
    }

    private record NodeDepth(Element node, int depth) {
    }

    private static final class SilentErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
