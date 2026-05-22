SUMMARY = "EdgeOS headless image — robotics, edge-AI, and CAN stack for i.MX8MP"
LICENSE  = "MIT"

# Start from NXP's minimal core image so BSP-specific fixups are inherited
require recipes-fsl/images/imx-image-core.bb

# ── Strip debug/developer features from the base ─────────────────────────────
# imx-image-core pulls debug-tweaks, tools-profile, tools-sdk, tools-debug.
# None of those belong in a CRA-compliant production image.
IMAGE_FEATURES:remove = " \
    debug-tweaks \
    tools-profile \
    tools-sdk \
    tools-debug \
    splash \
"

# Keep ssh access and package management for field updates
IMAGE_FEATURES += " \
    ssh-server-openssh \
    package-management \
    hwcodecs \
"

# ── Root password (SHA-512, default: edgetest2026) ────────────────────────────
# Regenerate with: openssl passwd -6 -salt '<salt>' '<password>'
# and update EXTRA_USERS_PARAMS below before production release.
inherit extrausers
EXTRA_USERS_PARAMS = "usermod -p '\$6\$edgeos2026\$e8JA/mlEsXOShDKssk/5BUM1VV4PAmcliwnw5vEiWlxPw/ElI85o9ua1HIrSa9nyZ8MpdTavsXNtB8jUsipaf1' root;"

# ── Security hardening ────────────────────────────────────────────────────────
IMAGE_FEATURES:remove = "empty-root-password"
INHIBIT_PACKAGE_STRIP = "0"

# ── Core system packages ──────────────────────────────────────────────────────
IMAGE_INSTALL:append = " \
    packagegroup-imx-core-tools \
    packagegroup-imx-security \
"

# ── GStreamer + V4L2 (camera pipeline) ───────────────────────────────────────
IMAGE_INSTALL:append = " \
    gstreamer1.0 \
    gstreamer1.0-plugins-good \
    gstreamer1.0-plugins-bad \
    imx-gst1.0-plugin \
    v4l-utils \
    libv4l \
"

# ── Edge-AI / ML stack (NXP eIQ — TFLite 2.16 + VX NPU delegate) ─────────────
# packagegroup-imx-ml is intentionally NOT used here: it pulls pytorch and tvm,
# both of which are multi-hour, 10-16 GB C++ builds not needed for inference.
# We include only the inference-path packages.
IMAGE_INSTALL:append = " \
    tensorflow-lite \
    tensorflow-lite-vx-delegate \
    onnxruntime \
    nnstreamer \
    nnstreamer-tensorflow-lite \
    python3-core \
    python3-numpy \
    opencv \
    python3-opencv \
"

# ── YOLO object detection (ultralytics — ONNX Runtime inference backend) ──────
IMAGE_INSTALL:append = " \
    python3-ultralytics \
"

# ── Package manager compatibility ────────────────────────────────────────────
# apt / apt-get wrapper maps familiar Debian commands to opkg
IMAGE_INSTALL:append = " \
    apt-compat \
"

# ── Demo applications ─────────────────────────────────────────────────────────
# edgeos-detect    : USB webcam → YOLOv8n → live terminal detection output
# edgeos-benchmark : CPU / memory / storage / ML inference benchmark
# stress-ng        : hardware stress test (included via edgeos-demo RDEPENDS)
IMAGE_INSTALL:append = " \
    edgeos-demo \
"

# ── Performance control ───────────────────────────────────────────────────────
# edgeos-perfctl   : locks A53 cores at 1800 MHz, disables idle states,
#                    maximises GPU/NPU/DDR devfreq — with live monitor
# cpufrequtils     : cpufreq-info / cpufreq-set (standard Linux CPU freq tools)
# cpupower         : cpupower frequency-set / idle-set (kernel power interface)
IMAGE_INSTALL:append = " \
    edgeos-perfctl \
    cpufrequtils \
    cpupower \
"

# ── Industrial CAN bus + networking ──────────────────────────────────────────
IMAGE_INSTALL:append = " \
    can-utils \
    iproute2 \
"

# ── ROS2 Humble base stack ────────────────────────────────────────────────────
# ros-base is the minimal ROS2 metapackage (rclcpp, rclpy, std_msgs, etc.)
IMAGE_INSTALL:append = " \
    ros-base \
"

export IMAGE_BASENAME = "edgeos-image-headless"
