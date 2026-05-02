package de.mark225.bluebridge.core.util;

import org.apache.commons.text.lookup.StringLookup;

public abstract class StringLookupWrapper {

    public final StringLookup lookup = this::replace;

    public abstract String replace(String in);
}
