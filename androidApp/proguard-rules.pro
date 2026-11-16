# Ktor (sync with the OwnBudget server) refers to JVM-only classes that Android does not have and the
# app never reaches: SLF4J logging and the JMX management API used for debug detection.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
