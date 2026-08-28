# ical4j optionally integrates with Joda Convert for Interval#toString().
# The app does not use that optional integration, so retain R8 shrinking and
# suppress only its absent optional type warning.
-dontwarn org.joda.convert.ToString
