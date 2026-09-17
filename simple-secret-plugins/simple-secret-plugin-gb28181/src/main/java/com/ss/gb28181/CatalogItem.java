package com.ss.gb28181;

/** A device or administrative node returned by a GB28181 catalog response. */
public record CatalogItem(String deviceId, String name, String parentId, String status) {
}
