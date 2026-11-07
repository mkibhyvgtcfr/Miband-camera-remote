package com.miband.camremote.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PtpProtocolTest {
    @Test
    fun ptpStringRoundTrip() {
        val encoded = PtpWriter().ptpString("Nikon Z 6").toByteArray()
        assertEquals("Nikon Z 6", PtpReader(encoded).ptpString())
    }

    @Test
    fun deviceInfoParsesVendorAndModel() {
        val w = PtpWriter()
            .u16(100)
            .u32(0x0000000AL)
            .u16(100)
            .ptpString("Nikon")
            .u16(0)
            .u32(2).u16(Ptp.OP_GET_DEVICE_INFO).u16(Ptp.OP_OPEN_SESSION)
            .u32(1).u16(0x4002)
            .u32(2).u16(Ptp.DPC_EXPOSURE_INDEX).u16(Ptp.DPC_EXPOSURE_TIME)
            .u32(0)
            .u32(0)
            .ptpString("Nikon Corporation")
            .ptpString("Z 6")
            .ptpString("2.10")
            .ptpString("SN-123")
        val info = PtpParser.deviceInfo(w.toByteArray())
        assertEquals(0x0000000AL, info.vendorExtensionId)
        assertEquals("Nikon Corporation", info.manufacturer)
        assertEquals("Z 6", info.modelName)
        assertEquals(CameraBrand.NIKON, CameraBrand.from(info.manufacturer, info.modelName, info.vendorExtensionId))
        assertTrue(info.operationsSupported.contains(Ptp.OP_GET_DEVICE_INFO))
    }

    @Test
    fun propertyDescriptionParsesEnumAndCurrentValue() {
        val rawShutter = byteArrayOf(0xFA.toByte(), 0x00, 0x01, 0x00)
        val w = PtpWriter()
            .u16(Ptp.DATA_TYPE_UINT32)
            .u8(3)
            .u32(0x000100FA)
            .u32(0x000100FA)
            .u8(Ptp.FORM_ENUM)
            .u16(2)
            .u32(0x000100FA)
            .u32(0x0001007D)
        val desc = PtpParser.propertyDescription(w.toByteArray())
        assertEquals(Ptp.DATA_TYPE_UINT32, desc.dataType)
        assertEquals(2, desc.enumValues.size)
        assertArrayEquals(rawShutter, desc.currentRaw)
        assertTrue(desc.writable)
        val spec = PropertyCatalog.forBrand(CameraBrand.NIKON).first { it.key == "shutter" }
        assertEquals("1/250", PropertyFormat.display(spec, desc.currentRaw, desc.dataType))
        assertEquals("1/125", PropertyFormat.display(spec, desc.enumValues[1].raw, desc.dataType))
    }

    @Test
    fun propertyDescriptionTreatsGetSetOneAsWritable() {
        // The PTP GetSet field is 0 = read-only, 1 = read/write. It is not a
        // bit mask; Nikon returns 1 for ordinary settable properties.
        val w = PtpWriter()
            .u16(Ptp.DATA_TYPE_UINT8)
            .u8(1)
            .u8(0)
            .u8(1)
            .u8(Ptp.FORM_ENUM)
            .u16(2)
            .u8(0)
            .u8(1)
        val desc = PtpParser.propertyDescription(w.toByteArray())
        assertTrue(desc.readable)
        assertTrue(desc.writable)
        assertEquals(2, desc.enumValues.size)
    }

    @Test
    fun canonTablesRenderFriendlyLabels() {
        val iso = PropertyCatalog.forBrand(CameraBrand.CANON).first { it.key == "iso" }
        val shutter = PropertyCatalog.forBrand(CameraBrand.CANON).first { it.key == "shutter" }
        val aperture = PropertyCatalog.forBrand(CameraBrand.CANON).first { it.key == "aperture" }
        assertEquals("AUTO", PropertyFormat.display(iso, Le.u32Bytes(0)))
        assertEquals("100", PropertyFormat.display(iso, Le.u32Bytes(0x48)))
        assertEquals("800", PropertyFormat.display(iso, Le.u32Bytes(0x60)))
        assertEquals("1/250", PropertyFormat.display(shutter, Le.u32Bytes(0x75)))
        assertEquals("f/2.8", PropertyFormat.display(aperture, Le.u32Bytes(0x20)))
        assertTrue(iso.fixedOptions!!.any { it.label == "6400" })
    }

    @Test
    fun nikonLiveViewPayloadJpegIsExtracted() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val payload = ByteArray(128) + jpeg + ByteArray(4)
        val extracted = PtpParser.findJpeg(payload)
        assertNotNull(extracted)
        assertArrayEquals(jpeg, extracted)
    }

    @Test
    fun canonEosPayloadSkipsLengthHeader() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 8, 0xFF.toByte(), 0xD9.toByte())
        val payload = Le.u32Bytes(jpeg.size.toLong()) + Le.u32Bytes(1) + jpeg
        assertArrayEquals(jpeg, PtpParser.findJpeg(payload))
    }

    @Test
    fun signedPtpValueHandlesNegativeExposureComp() {
        val value = PtpReader(byteArrayOf(0xF8.toByte(), 0xFF.toByte())).value(Ptp.DATA_TYPE_INT16)
        assertEquals(-8L, value.number)
    }

    @Test
    fun everyBrandExposesPrimaryExposureControls() {
        for (brand in listOf(CameraBrand.NIKON, CameraBrand.CANON, CameraBrand.GENERIC)) {
            val keys = PropertyCatalog.forBrand(brand).map { it.key }
            assertTrue("$brand iso", keys.contains("iso"))
            assertTrue("$brand shutter", keys.contains("shutter"))
            assertTrue("$brand aperture", keys.contains("aperture"))
            assertTrue("$brand ev", keys.contains("ev"))
        }
    }
}
