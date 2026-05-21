# TVM is excluded from this build (multi-hour LLVM compile, not needed for
# inference-only deployment). Override the mx8mp SOC default which enables it.
PACKAGECONFIG_SOC:mx8mp-nxp-bsp = "tensorflow-lite"
