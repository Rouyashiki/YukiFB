package com.anatdx.yukifb.model

/**
 * Stable, backend-defined identity for an entry.
 *
 * The value is deliberately opaque. UI code must never parse it or assume it is
 * a filesystem path.
 */
@JvmInline
value class EntryId(val value: String) {
    override fun toString(): String = value
}
