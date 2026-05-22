SUMMARY     = "apt/apt-get compatibility wrapper for opkg"
DESCRIPTION = "Installs /usr/bin/apt and /usr/bin/apt-get shell wrappers that \
map familiar Debian/Ubuntu apt commands to their opkg equivalents."
LICENSE     = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://apt-wrapper"

S = "${WORKDIR}"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/apt-wrapper ${D}${bindir}/apt
    ln -sf apt ${D}${bindir}/apt-get
}

FILES:${PN} = "${bindir}/apt ${bindir}/apt-get"
