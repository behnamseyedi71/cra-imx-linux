SUMMARY     = "EdgeOS demo applications (object detection and system benchmark)"
DESCRIPTION = "Provides two command-line tools: edgeos-detect (live USB webcam \
object detection via YOLOv8n ONNX, running on the i.MX8MP NPU by default) and \
edgeos-benchmark (CPU/memory/storage/ML inference benchmark). The YOLOv8n ONNX \
model (~12 MB) is pre-installed under /var/lib/edgeos-demo/ so the demo runs on \
first boot without an internet connection."
# The Python scripts and recipe are MIT. The YOLOv8n ONNX model is generated
# from ultralytics weights and inherits the upstream ultralytics AGPL-3.0
# licence; declare both so SPDX SBOM reflects reality.
LICENSE     = "MIT & AGPL-3.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302 \
                    file://${COMMON_LICENSE_DIR}/AGPL-3.0-only;md5=73f1eb20517c55bf9493b7dd6e480788"

SRC_URI = " \
    file://edgeos-detect \
    file://edgeos-benchmark \
    file://yolov8n.onnx \
"

S = "${WORKDIR}"

# Inference path is pure onnxruntime + OpenCV, no ultralytics/torch import,
# so the script actually runs on the minimal headless image.
RDEPENDS:${PN} = " \
    python3-core \
    python3-opencv \
    python3-numpy \
    onnxruntime \
    stress-ng \
"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/edgeos-detect     ${D}${bindir}/edgeos-detect
    install -m 0755 ${WORKDIR}/edgeos-benchmark  ${D}${bindir}/edgeos-benchmark

    # Pre-install the YOLOv8n ONNX model so the demo works on first boot
    # without an internet connection. Mode 0644 (world-readable but not
    # writable). The model is immutable shipped content.
    install -d ${D}/var/lib/edgeos-demo
    install -m 0644 ${WORKDIR}/yolov8n.onnx ${D}/var/lib/edgeos-demo/yolov8n.onnx
}

FILES:${PN} += " \
    ${bindir}/edgeos-detect \
    ${bindir}/edgeos-benchmark \
    /var/lib/edgeos-demo \
    /var/lib/edgeos-demo/yolov8n.onnx \
"
