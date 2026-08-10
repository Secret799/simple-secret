package com.ss.kmz.kml;

import com.ss.kmz.domain.Coordinate;
import com.ss.kmz.exception.KmzException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmlLineStringReaderTest {

    @Test
    void shouldReadLineStringsFromPlacemarkAndMultiGeometry() {
        String kml = """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Document>
                    <Placemark>
                      <name>测试管线</name>
                      <MultiGeometry>
                        <LineString><coordinates>110.1,36.1 110.2,36.2,3</coordinates></LineString>
                        <LineString><coordinates>111,37,4 112,38,5</coordinates></LineString>
                      </MultiGeometry>
                    </Placemark>
                  </Document>
                </kml>
                """;

        List<KmlLineString> lines = KmlLineStringReader.read(kml);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).name()).isEqualTo("测试管线");
        assertThat(lines.get(0).coordinates()).containsExactly(
                new Coordinate(110.1, 36.1, 0),
                new Coordinate(110.2, 36.2, 3));
        assertThatThrownBy(() -> lines.get(0).coordinates().add(new Coordinate()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldIgnoreForeignNamespaceElementsWithMatchingLocalNames() {
        String kml = """
                <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:x="urn:foreign">
                  <Document>
                    <x:Placemark><x:LineString><x:coordinates>1,2,3 4,5,6</x:coordinates></x:LineString></x:Placemark>
                  </Document>
                </kml>
                """;

        assertThat(KmlLineStringReader.read(kml)).isEmpty();
    }

    @Test
    void shouldRejectMalformedCoordinateTuple() {
        String kml = """
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark><LineString><coordinates>110.1</coordinates></LineString></Placemark>
                </kml>
                """;

        assertThatThrownBy(() -> KmlLineStringReader.read(kml))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("110.1");
    }

    @Test
    void shouldRejectDtd() {
        String kml = """
                <?xml version="1.0"?>
                <!DOCTYPE kml [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Placemark><name>&xxe;</name></Placemark>
                </kml>
                """;

        assertThatThrownBy(() -> KmlLineStringReader.read(kml))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("KML LineString");
    }
}
