package com.ss.gb28181;

/** Receives Catalog NOTIFY batches admitted by a GB28181 server. */
@FunctionalInterface
public interface GbCatalogListener {

    void onCatalog(GbCatalogEvent event);
}
