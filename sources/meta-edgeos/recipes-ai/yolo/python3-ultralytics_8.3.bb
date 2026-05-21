SUMMARY     = "Ultralytics YOLOv8 — object detection and image segmentation"
DESCRIPTION = "Ultralytics YOLO framework. Deployed in inference-only mode \
on i.MX8MP using the ONNX Runtime backend with NXP VX NPU delegate."
HOMEPAGE    = "https://github.com/ultralytics/ultralytics"
LICENSE     = "AGPL-3.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=eb1e647870add0502f8f010b19de32af"

# Build backend is setuptools.build_meta (not hatchling)
inherit pypi python_setuptools_build_meta

PYPI_PACKAGE = "ultralytics"
PV           = "8.3.119"

SRC_URI[sha256sum] = "497bdcf3eb1beb082f451d42e5af2a6af944693a5991c78a9b9b0ce538593153"

# Yocto Scarthgap ships setuptools 69.1.1; relax the >=70 build constraint.
do_configure:prepend() {
    sed -i 's/setuptools>=70\.0\.0/setuptools>=69.0.0/' ${S}/pyproject.toml
}

# ── Runtime dependencies (inference path only — no PyTorch training stack) ───
RDEPENDS:${PN} = " \
    python3-core \
    python3-numpy \
    python3-opencv \
    python3-pillow \
    python3-pyyaml \
    python3-requests \
    python3-psutil \
    python3-tqdm \
    onnxruntime \
"

# Silence ultralytics' torch-not-found warning at runtime.
# Also strip training-only data-download shell scripts — they require /bin/bash
# which is not a runtime dep for an inference-only deployment.
do_install:append() {
    install -d ${D}${sysconfdir}/profile.d
    echo "export YOLO_SKIP_TORCH_CHECK=1" > ${D}${sysconfdir}/profile.d/ultralytics.sh
    find ${D}${libdir} -path "*/ultralytics/data/scripts/*.sh" -delete
}

FILES:${PN} += "${sysconfdir}/profile.d/ultralytics.sh"
