package com.ss.gb28181;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;

final class GbMobilePositionValidation {
    private GbMobilePositionValidation() {
    }

    static void dateTime(String value, String name) {
        if (value.length() > 64) {
            throw new IllegalArgumentException(name + " must be an XML dateTime of at most 64 characters");
        }
        try {
            var calendar = DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(value);
            if (!DatatypeConstants.DATETIME.equals(calendar.getXMLSchemaType()) || !calendar.isValid()) {
                throw new IllegalArgumentException(name + " must be a valid XML dateTime");
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a valid XML dateTime", exception);
        }
    }
}
