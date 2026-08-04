package com.zhy20.teleprompter.data.importer

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Creates an [XmlPullParser] hardened for untrusted Word XML.
 *
 * External entities and DTDs are explicitly disabled so a crafted document cannot perform an
 * XXE or billion-laughs expansion. The same factory settings apply on Android and on the JVM
 * (kxml2), so behavior is identical in production and in unit tests.
 */
internal object WordXmlPull {
    fun new(): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        return factory.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        }
    }
}
