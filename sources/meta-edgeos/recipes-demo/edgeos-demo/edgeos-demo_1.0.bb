SUMMARY     = "EdgeOS demo applications — object detection and system benchmark"
DESCRIPTION = "Provides two command-line tools: edgeos-detect (live USB webcam \
object detection via YOLOv8n ONNX) and edgeos-benchmark (CPU/memory/storage/ML \
inference benchmark). The YOLOv8n ONNX model (~12 MB) is downloaded on first run \
to /var/lib/edgeos-demo/ if not already present."
LICENSE     = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://edgeos-detect \
    file://edgeos-benchmark \
"

S = "${WORKDIR}"

RDEPENDS:${PN} = " \
    python3-core \
    python3-ultralytics \
    python3-opencv \
    python3-numpy \
    onnxruntime \
    stress-ng \
"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/edgeos-detect     ${D}${bindir}/edgeos-detect
    install -m 0755 ${WORKDIR}/edgeos-benchmark  ${D}${bindir}/edgeos-benchmark

    # Pre-create model cache directory with open permissions so root and
    # any future non-root user can write the downloaded model on first run.
    install -d -m 0777 ${D}/var/lib/edgeos-demo
}

FILES:${PN} += " \
    ${bindir}/edgeos-detect \
    ${bindir}/edgeos-benchmark \
    /var/lib/edgeos-demo \
"
