package com.ss.ics.dahua;

import org.junit.jupiter.api.Test;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import static org.assertj.core.api.Assertions.assertThat;

class DahuaJnaStructuresTest {

    @Test
    void usesFixedWidthHandlesAndInlinePreviewPathBuffer() {
        assertThat(Native.getNativeSize(
                DahuaJnaStructures.DahuaLong.class,
                new DahuaJnaStructures.DahuaLong())).isEqualTo(8);
        assertThat(new DahuaJnaStructures.RealPlayInput().saveFileName).hasSize(260);
    }

    @Test
    void preservesHighSecurityLoginAbiLayout() {
        DahuaJnaStructures.HighSecurityLoginInput input =
                new DahuaJnaStructures.HighSecurityLoginInput();
        input.port = 0x10203040;
        input.specialCapability = 0x50607080;
        input.capabilityParameter = new Pointer(0x11223344L);
        input.tlsCapability = 0x0a0b0c0d;
        input.write();

        assertThat(input.size()).isEqualTo(224);
        assertThat(input.getPointer().getInt(68)).isEqualTo(0x10203040);
        assertThat(input.getPointer().getInt(200)).isEqualTo(0x50607080);
        assertThat(Pointer.nativeValue(input.getPointer().getPointer(208)))
                .isEqualTo(0x11223344L);
        assertThat(input.getPointer().getInt(216)).isEqualTo(0x0a0b0c0d);

        assertThat(new DahuaJnaStructures.DeviceInfoEx().size()).isEqualTo(100);
        assertThat(new DahuaJnaStructures.HighSecurityLoginOutput().size()).isEqualTo(240);
    }

    @Test
    void preservesRawPreviewAbiLayout() {
        DahuaJnaStructures.RealPlayInput input = new DahuaJnaStructures.RealPlayInput();
        input.audioType = 0x10203040;
        input.mp4Type = 0x50607080;
        input.write();

        assertThat(input.size()).isEqualTo(344);
        assertThat(input.getPointer().getInt(320)).isEqualTo(0x10203040);
        assertThat(input.getPointer().getInt(336)).isEqualTo(0x50607080);
        assertThat(new DahuaJnaStructures.DataCallbackInfo().size()).isEqualTo(80);
    }

    @Test
    void preservesRadiometryAbiLayout() {
        DahuaJnaStructures.ThermalMetadata metadata =
                new DahuaJnaStructures.ThermalMetadata();
        metadata.length = 0x10203040;
        metadata.unzipR = 0x50607080;
        metadata.write();

        assertThat(metadata.size()).isEqualTo(376);
        assertThat(metadata.getPointer().getInt(36)).isEqualTo(0x10203040);
        assertThat(metadata.getPointer().getInt(104)).isEqualTo(0x50607080);
        assertThat(new DahuaJnaStructures.ThermalData().size()).isEqualTo(904);
        assertThat(new DahuaJnaStructures.RadiometryRecord().size()).isEqualTo(452);
        assertThat(new DahuaJnaStructures.RadiometryPageOutput().size()).isEqualTo(14_472);
    }

    @Test
    void initializesAllNestedRadiometryStructures() {
        assertThat(new DahuaJnaStructures.PointTemperatureInput().coordinate).isNotNull();
        assertThat(new DahuaJnaStructures.PointTemperatureOutput().temperature).isNotNull();
        assertThat(new DahuaJnaStructures.ItemTemperatureInput().condition).isNotNull();
        assertThat(new DahuaJnaStructures.ItemTemperatureOutput().temperature).isNotNull();
        assertThat(new DahuaJnaStructures.ThermalData().metadata).isNotNull();
        assertThat(new DahuaJnaStructures.ThermalMetadata().time).isNotNull();
        assertThat(new DahuaJnaStructures.RadiometryRecord().temperature).isNotNull();
        assertThat(new DahuaJnaStructures.RadiometryPageOutput().records).hasSize(32);
        assertThat(new DahuaJnaStructures.RegionTemperatureInput().polygon)
                .hasSize(8).doesNotContainNull();
    }
}
