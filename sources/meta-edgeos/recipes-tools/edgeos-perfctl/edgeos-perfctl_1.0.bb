SUMMARY     = "EdgeOS CPU/GPU/NPU/DDR performance control for i.MX8MP"
DESCRIPTION = "Provides edgeos-perfctl: locks all 4 Cortex-A53 cores at 1800 MHz \
(performance governor), disables deep-idle C-states for minimum latency, and sets \
GPU/NPU/DDR devfreq governors to performance mode. Includes a systemd service for \
applying max performance on boot and a live monitor dashboard."
LICENSE     = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://edgeos-perfctl \
    file://edgeos-performance.service \
"

S = "${WORKDIR}"

inherit systemd

# Service is installed but NOT enabled by default. The user has to opt in:
#   systemctl enable edgeos-performance
SYSTEMD_SERVICE:${PN} = "edgeos-performance.service"
SYSTEMD_AUTO_ENABLE = "disable"

RDEPENDS:${PN} = "python3-core"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/edgeos-perfctl ${D}${bindir}/edgeos-perfctl

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/edgeos-performance.service \
                    ${D}${systemd_system_unitdir}/edgeos-performance.service
}

FILES:${PN} = " \
    ${bindir}/edgeos-perfctl \
    ${systemd_system_unitdir}/edgeos-performance.service \
"
