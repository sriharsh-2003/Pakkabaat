# Add project specific ProGuard rules here.
-keep class com.pakkabaat.app.data.model.** { *; }
-keep class com.pakkabaat.app.network.** { *; }
# SessionMessage and its sealed subclasses (Hello, StopRequest, DraftReady, etc.) are
# serialized/deserialized by Gson via reflection over their own field names. Not covered
# by the two rules above (different package) — if minification is ever turned on without
# this, Gson's field-name matching breaks silently once field names get obfuscated, which
# would reintroduce exactly the kind of broken pairing/stop-request bugs already fixed.
-keep class com.pakkabaat.app.pairing.** { *; }
