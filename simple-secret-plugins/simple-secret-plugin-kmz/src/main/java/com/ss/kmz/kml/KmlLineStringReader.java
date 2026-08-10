package com.ss.kmz.kml;

import com.ss.kmz.KmlReader;
import com.ss.kmz.constants.DjiNamespaces;
import com.ss.kmz.domain.Coordinate;
import com.ss.kmz.exception.KmzException;
import com.ss.kmz.internal.XmlSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 安全读取普通 KML 中的 Placemark/LineString 管线。
 */
public final class KmlLineStringReader {

    private KmlLineStringReader() {
    }

    /** 从文件读取 LineString。 */
    public static List<KmlLineString> read(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("KML path must not be null");
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            return read(inputStream);
        } catch (IOException exception) {
            throw new KmzException("读取 KML LineString 文件失败: " + path, exception);
        }
    }

    /** 从字符串读取 LineString。 */
    public static List<KmlLineString> read(String kml) {
        if (kml == null) {
            throw new IllegalArgumentException("KML string must not be null");
        }
        byte[] bytes = kml.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > KmlReader.DEFAULT_MAX_BYTES) {
            throw new KmzException("KML LineString exceeds maximum size of "
                    + KmlReader.DEFAULT_MAX_BYTES + " bytes");
        }
        return parse(bytes);
    }

    /** 从输入流读取 LineString，输入流不会被关闭。 */
    public static List<KmlLineString> read(InputStream inputStream) {
        return read(inputStream, KmlReader.DEFAULT_MAX_BYTES);
    }

    /** 从输入流读取 LineString 并限制最大字节数，输入流不会被关闭。 */
    public static List<KmlLineString> read(InputStream inputStream, int maxBytes) {
        return parse(XmlSupport.readLimited(inputStream, maxBytes, "KML LineString"));
    }

    private static List<KmlLineString> parse(byte[] bytes) {
        try {
            DocumentBuilder builder = newFactory().newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            Document document = builder.parse(new ByteArrayInputStream(bytes));
            List<KmlLineString> result = new ArrayList<>();
            NodeList placemarks = document.getElementsByTagNameNS("*", DjiNamespaces.PLACEMARK);
            for (int index = 0; index < placemarks.getLength(); index++) {
                Element placemark = (Element) placemarks.item(index);
                if (!isKml(placemark, DjiNamespaces.PLACEMARK)) {
                    continue;
                }
                String name = directChildText(placemark, DjiNamespaces.NAME);
                NodeList lines = placemark.getElementsByTagNameNS("*", DjiNamespaces.LINE_STRING);
                for (int lineIndex = 0; lineIndex < lines.getLength(); lineIndex++) {
                    Element line = (Element) lines.item(lineIndex);
                    if (!isKml(line, DjiNamespaces.LINE_STRING)) {
                        continue;
                    }
                    String text = directChildText(line, DjiNamespaces.COORDINATES);
                    List<Coordinate> coordinates = parseCoordinates(text);
                    if (!coordinates.isEmpty()) {
                        result.add(new KmlLineString(blankToNull(name), coordinates));
                    }
                }
            }
            return List.copyOf(result);
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new KmzException("解析 KML LineString 失败", exception);
        }
    }

    private static List<Coordinate> parseCoordinates(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Coordinate> coordinates = new ArrayList<>();
        for (String tuple : text.trim().split("\\s+")) {
            try {
                coordinates.add(Coordinate.fromKml(tuple));
            } catch (IllegalArgumentException exception) {
                throw new KmzException("invalid KML LineString coordinate: " + tuple, exception);
            }
        }
        return coordinates;
    }

    private static DocumentBuilderFactory newFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static String directChildText(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && isKml(element, localName)) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }

    private static boolean isKml(Element element, String localName) {
        String namespace = element.getNamespaceURI();
        String actualName = element.getLocalName() == null ? element.getTagName() : element.getLocalName();
        return localName.equals(actualName)
                && (DjiNamespaces.KML_NAMESPACE.equals(namespace) || namespace == null || namespace.isEmpty());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
